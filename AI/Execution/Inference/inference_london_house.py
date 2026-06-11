import numpy as np
import pandas as pd
import json

from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel
from AI.Domain.Datasets.london_house_loader import LondonSingleHouseLoader


def get_window(df, target_time, lookback_hours):
    target_time = pd.to_datetime(target_time)

    df = df.copy()
    df["DateTime"] = pd.to_datetime(df["DateTime"])

    df = df[df["DateTime"] <= target_time]
    df = df.sort_values("DateTime")

    cutoff = target_time - pd.Timedelta(hours=lookback_hours)
    return df[df["DateTime"] >= cutoff]


def build_input(values, window_size):
    return values[-window_size:].reshape(1, -1)


def forecast(model, values, window_size, horizon):
    window = values[-window_size:].copy()
    preds = []

    for _ in range(horizon):
        pred = model.predict(window.reshape(1, -1))[0]
        preds.append(pred)
        window = np.roll(window, -1)
        window[-1] = pred

    return np.array(preds)


def ngboost_stats(model, X):

    dist = model.predict_distribution(X)

    mean = dist.mean
    std = dist.scale

    lower = dist.ppf(0.025)
    upper = dist.ppf(0.975)

    return mean, std, lower, upper


def main():

    # ----------------------------
    # CONFIG
    # ----------------------------
    with open("../../Domain/Resources/LondonHouse/inference_config.json", "r") as f:
        config = json.load(f)

    model_type = config["model"]["type"].lower()
    model_path = config["model"]["path"]

    house_id = config["dataset"]["house_id"]

    window_size = config["features"]["window_size"]
    lookback_hours = config["features"]["lookback_hours"]
    horizon = config["features"]["horizon"]

    # ----------------------------
    # MODEL
    # ----------------------------
    if model_type == "ngboost":
        model = NGBoostModel()
    elif model_type == "xgboost":
        model = XGBoostModel()
    else:
        raise ValueError(f"Unknown model type: {model_type}")

    model.load(model_path)

    # ----------------------------
    # DATA (mock now)
    # ----------------------------
    loader = LondonSingleHouseLoader(
        folder_path=""
    )

    df = loader.load_houses(house_id)

    target_time = df["DateTime"].max()

    df = get_window(df, target_time, lookback_hours)

    values = df["KWH/hh (per half hour)"].values

    # ----------------------------
    # PREDICTION
    # ----------------------------
    X = build_input(values, window_size)

    next_pred = model.predict(X)[0]

    if model_type == "ngboost":
        mean, std, lower, upper = ngboost_stats(model, X)

    horizon_pred = forecast(
        model=model,
        values=values,
        window_size=window_size,
        horizon=horizon
    )

    # ----------------------------
    # OUTPUT
    # ----------------------------
    print("\n======================")
    print(f"House: {house_id}")
    print(f"Model: {model_type}")
    print(f"Next prediction: {next_pred:.4f}")

    if model_type == "ngboost":
        print()
        print("NGBoost uncertainty:")
        print(f"Mean: {mean[0]:.4f}")
        print(f"Std: {std[0]:.4f}")
        print(
            f"95% interval: "
            f"[{lower[0]:.4f}, {upper[0]:.4f}]"
        )

    print("======================\n")


if __name__ == "__main__":
    main()