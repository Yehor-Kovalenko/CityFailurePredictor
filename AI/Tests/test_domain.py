import json

import numpy as np
import pandas as pd
import pytest

from AI.Domain.Datasets.feature_builder_london_house import FeatureLondonHouseBuilder
from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder
from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Execution.Training import trainer_energy, trainer_traffic

KWH = "KWH/hh (per half hour)"


def house(house_id, values, start="2013-01-01"):
    return pd.DataFrame({
        "LCLid": house_id,
        "DateTime": pd.date_range(start, periods=len(values), freq="30min"),
        KWH: values,
    })


def junction(junction_id, n=12, start="2017-03-01"):
    return pd.DataFrame({
        "DateTime": pd.date_range(start, periods=n, freq="h"),
        "Vehicles": np.arange(n) + 10 * junction_id,
        "Junction": junction_id,
    })


# =========================================================
# ELECTRICITY FEATURES
# =========================================================
def test_house_windows_never_mix_houses():
    df = pd.concat([house("A", [0.1] * 6), house("B", [5.0] * 6)])

    X, y = FeatureLondonHouseBuilder(window_size=4).transform(df)

    assert len(X) == 4
    for window, target in zip(X.to_numpy(), y):
        assert set(window) | {target} in ({0.1}, {5.0})


def test_house_windows_follow_time_order_and_skip_short_houses():
    shuffled = house("A", [1.0, 2.0, 3.0, 4.0, 5.0]).sample(frac=1, random_state=0)
    df = pd.concat([shuffled, house("B", [9.0, 9.0])])

    X, y = FeatureLondonHouseBuilder(window_size=3).transform(df)

    assert X.to_numpy().tolist() == [[1.0, 2.0, 3.0], [2.0, 3.0, 4.0]]
    assert y.tolist() == [4.0, 5.0]


def test_house_builder_without_enough_history_returns_no_samples():
    X, y = FeatureLondonHouseBuilder(window_size=24).transform(house("A", [1.0] * 5))

    assert X.shape == (0, 24)
    assert len(y) == 0


# =========================================================
# TRAFFIC FEATURES
# =========================================================
def test_junction_code_is_the_same_alone_and_in_full_dataset():
    builder = TrafficFeatureBuilder(window_size=6, junction_ids=[1, 2, 3, 4])

    X_all, _ = builder.transform(pd.concat([junction(j) for j in (1, 2, 3, 4)]))
    X_single, _ = builder.transform(junction(3))

    assert sorted(set(X_all[:, -1])) == [0, 1, 2, 3]
    assert set(X_single[:, -1]) == {2}


def test_unknown_junction_is_rejected():
    builder = TrafficFeatureBuilder(window_size=6, junction_ids=[1, 2, 3, 4])

    with pytest.raises(ValueError, match="Unknown junctions"):
        builder.transform(junction(9))


# =========================================================
# MODELS
# =========================================================
def test_xgboost_interval_uses_held_out_residuals():
    rng = np.random.default_rng(0)
    X = rng.random((300, 4))
    y = X.sum(axis=1) + rng.normal(0, 0.5, 300)

    model = XGBoostModel(n_estimators=200)
    model.train(X[:200], y[:200])
    train_std = model.residual_std

    model.calibrate(X[200:], y[200:])

    assert model.residual_std > train_std

    lower, upper = model.predict_interval(X[200:])
    assert np.all(lower < upper)


def test_xgboost_interval_requires_fitted_spread():
    with pytest.raises(ValueError):
        XGBoostModel().predict_interval(np.zeros((1, 4)))


def test_ngboost_honours_max_depth():
    assert NGBoostModel(max_depth=5).model.Base.max_depth == 5


# =========================================================
# TRAINING SCRIPTS
# =========================================================
def read_run(run_dir):
    assert (run_dir / "model.joblib").exists()
    return json.loads((run_dir / "metadata.json").read_text())


def test_energy_trainer_runs_from_any_directory(tmp_path, monkeypatch):
    rng = np.random.default_rng(1)
    data_dir = tmp_path / "lcl"
    data_dir.mkdir()

    n = 600
    values = 0.5 + 0.3 * np.sin(np.arange(n) / 48 * 2 * np.pi) + rng.normal(0, 0.02, n)
    house("MAC1", values).to_csv(data_dir / "part.csv", index=False)

    config = {
        "dataset": {"house_id": "MAC1"},
        "split": {"train_end": "2013-01-08", "val_end": "2013-01-11", "test_end": "2013-01-14"},
        "features": {"window_size": 24},
        "model": {"type": "xgboost"},
    }
    monkeypatch.chdir(tmp_path)

    run_dir = trainer_energy.run(config, str(data_dir), str(tmp_path / "out"))

    metadata = read_run(run_dir)
    assert metadata["house_ids"] == ["MAC1"]
    assert metadata["test"]["model"]["mae"] < metadata["test"]["persistence_baseline"]["mae"] * 2
    assert 0 <= metadata["test"]["interval_95_coverage"] <= 1
    assert metadata["packages"]["xgboost"]


def test_traffic_trainer_records_junctions(tmp_path):
    csv_path = tmp_path / "traffic.csv"
    pd.concat([junction(j, n=24 * 20, start="2017-01-01") for j in (1, 2)]).to_csv(csv_path, index=False)

    config = {
        "model": {"type": "xgboost"},
        "features": {"window_size": 6, "use_hour": True, "use_day_of_week": True},
        "split": {"train_end": "2017-01-12", "val_end": "2017-01-16", "test_end": "2017-01-20"},
    }

    run_dir = trainer_traffic.run(config, str(csv_path), str(tmp_path / "out"))

    assert read_run(run_dir)["junction_ids"] == [1, 2]


def test_trainer_reports_empty_split(tmp_path):
    csv_path = tmp_path / "traffic.csv"
    junction(1, n=48, start="2017-01-01").to_csv(csv_path, index=False)

    config = {
        "model": {"type": "xgboost"},
        "features": {"window_size": 6, "use_hour": True, "use_day_of_week": True},
        "split": {"train_end": "2017-01-02", "val_end": "2017-01-03", "test_end": "2018-01-01"},
    }

    with pytest.raises(ValueError, match="test split has no samples"):
        trainer_traffic.run(config, str(csv_path), str(tmp_path))
