import argparse
import json
import os
from datetime import datetime, timezone
from importlib.metadata import PackageNotFoundError, version
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel

REPO_ROOT = Path(__file__).resolve().parents[3]
RESOURCES_DIR = REPO_ROOT / "AI" / "Domain" / "Resources"
CONFIGS_DIR = RESOURCES_DIR / "Configs"
WEIGHTS_DIR = RESOURCES_DIR / "ModelWeights"

TRACKED_PACKAGES = ("numpy", "pandas", "scikit-learn", "scipy", "xgboost", "ngboost", "joblib")


def parse_args(description, data_env, data_help, data_default=None, argv=None):
    parser = argparse.ArgumentParser(description=description)

    parser.add_argument(
        "--data-path",
        default=os.environ.get(data_env, data_default),
        help=f"{data_help} (env: {data_env})",
    )
    parser.add_argument(
        "--output-dir",
        default=None,
        help="Where to create the run folder; defaults to the model weights folder of this task",
    )

    args = parser.parse_args(argv)

    if not args.data_path:
        parser.error(f"--data-path or {data_env} is required")

    return args


def load_train_config(task):
    with open(CONFIGS_DIR / task / "train_config.json", "r") as f:
        return json.load(f)


def split_by_time(df, split_config, time_column="DateTime"):
    train_end = pd.to_datetime(split_config["train_end"])
    val_end = pd.to_datetime(split_config["val_end"])
    test_end = pd.to_datetime(split_config["test_end"])

    train_df = df[df[time_column] < train_end]
    val_df = df[(df[time_column] >= train_end) & (df[time_column] < val_end)]
    test_df = df[(df[time_column] >= val_end) & (df[time_column] <= test_end)]

    print("\n=== SPLIT ===")
    print(f"Train: {train_df.shape}  (before {train_end})")
    print(f"Val:   {val_df.shape}  (before {val_end})")
    print(f"Test:  {test_df.shape}  (until {test_end})")

    return train_df, val_df, test_df


def build_model(model_type):

    if model_type == "ngboost":
        return NGBoostModel()

    if model_type == "xgboost":
        return XGBoostModel()

    raise ValueError(f"Unsupported model type: {model_type}")


def require_rows(name, X):
    if len(X) == 0:
        raise ValueError(f"The {name} split has no samples; check the split dates and the data")


def _metrics(y_true, y_pred):
    return {
        "mae": float(mean_absolute_error(y_true, y_pred)),
        "rmse": float(np.sqrt(mean_squared_error(y_true, y_pred))),
        "r2": float(r2_score(y_true, y_pred)),
    }


def evaluate(model, X, y, last_value_column):
    """Scores the model against the "next value equals the last value" baseline."""

    X = np.asarray(X)
    y = np.asarray(y)

    scores = {
        "samples": int(len(y)),
        "model": _metrics(y, model.predict(X)),
        "persistence_baseline": _metrics(y, X[:, last_value_column]),
    }

    baseline_mae = scores["persistence_baseline"]["mae"]
    scores["mae_skill_vs_persistence"] = (
        float(1 - scores["model"]["mae"] / baseline_mae) if baseline_mae > 0 else None
    )

    lower, upper = model.predict_interval(X, confidence=0.95)
    scores["interval_95_coverage"] = float(np.mean((y >= lower) & (y <= upper)))

    return scores


def print_scores(name, scores):
    print(f"\n=== {name} ===")
    print(f"MAE:  {scores['model']['mae']:.5f}  (persistence {scores['persistence_baseline']['mae']:.5f})")
    print(f"RMSE: {scores['model']['rmse']:.5f}  (persistence {scores['persistence_baseline']['rmse']:.5f})")
    print(f"R2:   {scores['model']['r2']:.4f}")
    print(f"95% interval coverage: {scores['interval_95_coverage']:.3f}")


def _package_versions():
    versions = {}

    for package in TRACKED_PACKAGES:
        try:
            versions[package] = version(package)
        except PackageNotFoundError:
            versions[package] = None

    return versions


def fit_evaluate_save(model_type, splits, window_size, task_weights_dir, metadata, output_dir=None):
    """Trains on the first split, calibrates on the second, scores both held-out splits and saves the run."""

    (X_train, y_train), (X_val, y_val), (X_test, y_test) = splits

    print("\n=== FEATURES ===")
    for name, X in (("train", X_train), ("validation", X_val), ("test", X_test)):
        print(f"{name}: {X.shape}")
        require_rows(name, X)

    model = build_model(model_type)

    print(f"\nTraining {model_type}...")
    model.train(X_train, y_train)

    if hasattr(model, "calibrate"):
        model.calibrate(X_val, y_val)

    val_scores = evaluate(model, X_val, y_val, last_value_column=window_size - 1)
    test_scores = evaluate(model, X_test, y_test, last_value_column=window_size - 1)

    print_scores("VALIDATION", val_scores)
    print_scores("TEST", test_scores)

    run_dir = save_run(
        model,
        task_weights_dir=task_weights_dir,
        model_type=model_type,
        metadata={**metadata, "validation": val_scores, "test": test_scores},
        output_dir=output_dir,
    )

    print(f"\nSaved model to: {run_dir}")

    return run_dir


def save_run(model, task_weights_dir, model_type, metadata, output_dir=None):
    """Writes model.joblib and metadata.json into <weights>/<model_type>/<timestamp>/."""

    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    base_dir = Path(output_dir) if output_dir else WEIGHTS_DIR / task_weights_dir
    run_dir = base_dir / model_type / timestamp

    run_dir.mkdir(parents=True, exist_ok=True)

    model.save(str(run_dir / "model.joblib"))

    metadata = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "model_type": model_type,
        **metadata,
        "packages": _package_versions(),
    }

    with open(run_dir / "metadata.json", "w") as f:
        json.dump(metadata, f, indent=2, default=str)

    return run_dir
