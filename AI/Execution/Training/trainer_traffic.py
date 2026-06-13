import json
import os
from datetime import datetime

import pandas as pd
from sklearn.metrics import mean_absolute_error

from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder
from AI.Domain.Datasets.traffic_dataset import TrafficDataset
from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel


# ----------------------------
# MAIN
# ----------------------------
def main():

    config_path = "../../Domain/Resources/Configs/traffic/train_config.json"

    with open(config_path, "r") as f:
        config = json.load(f)

    model_type = config["model"]["type"].lower()
    window_size = config["features"]["window_size"]

    train_end = pd.to_datetime(config["split"]["train_end"])
    val_end = pd.to_datetime(config["split"]["val_end"])
    test_end = pd.to_datetime(config["split"]["test_end"])

    # ----------------------------
    # MODEL
    # ----------------------------
    if model_type == "ngboost":
        model = NGBoostModel()

    elif model_type == "xgboost":
        model = XGBoostModel()

    else:
        raise ValueError(f"Unsupported model type: {model_type}")

    print("\n=== CONFIG ===")
    print(f"Model: {model_type}")
    print(f"Window: {window_size}")
    print(f"Train: {train_end}")
    print(f"Val:   {val_end}")
    print(f"Test:  {test_end}")

    # ----------------------------
    # LOAD DATA
    # ----------------------------
    print("\nLoading traffic dataset...")

    dataset = TrafficDataset("/home/agata/Documents/8/a lot of data/traffic.csv")

    df = dataset.load()


    # ----------------------------
    # SPLIT
    # ----------------------------
    train_df = df[df["DateTime"] < train_end]

    val_df = df[
        (df["DateTime"] >= train_end)
        & (df["DateTime"] < val_end)
    ]

    test_df = df[
        (df["DateTime"] >= val_end)
        & (df["DateTime"] <= test_end)
    ]

    print("\n=== SPLIT ===")
    print(f"Train: {train_df.shape}")
    print(f"Val:   {val_df.shape}")
    print(f"Test:  {test_df.shape}")

    # ----------------------------
    # FEATURES
    # ----------------------------
    builder = TrafficFeatureBuilder(
        window_size=config["features"]["window_size"],
        use_hour=config["features"]["use_hour"],
        use_day_of_week=config["features"]["use_day_of_week"],
        use_lag_24=config["features"]["use_lag_24"],
        use_lag_168=config["features"]["use_lag_168"],
        use_rolling_mean_24=config["features"]["use_rolling_mean_24"]
    )

    X_train, y_train = builder.transform(train_df)

    X_val, y_val = builder.transform(val_df)

    X_test, y_test = builder.transform(test_df)

    print("\n=== FEATURES ===")
    print(f"Train: {X_train.shape}")
    print(f"Val:   {X_val.shape}")
    print(f"Test:  {X_test.shape}")

    # ----------------------------
    # TRAIN
    # ----------------------------
    print(f"\nTraining {model_type}...")

    model.train(X_train, y_train)

    # ----------------------------
    # VALIDATION
    # ----------------------------
    val_preds = model.predict(X_val)

    val_mae = mean_absolute_error(
        y_val,
        val_preds
    )

    print("\n=== VALIDATION ===")
    print(f"MAE: {val_mae:.5f}")

    # ----------------------------
    # TEST
    # ----------------------------
    test_preds = model.predict(X_test)

    test_mae = mean_absolute_error(
        y_test,
        test_preds
    )

    print("\n=== TEST ===")
    print(f"MAE: {test_mae:.5f}")

    # ----------------------------
    # SAVE
    # ----------------------------
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")

    run_dir = os.path.join(
        "../../Domain/Resources/ModelWeights/traffic_weights",
        model_type,
        timestamp
    )

    os.makedirs(run_dir, exist_ok=True)

    model.save(
        os.path.join(run_dir, "model.joblib")
    )

    print(f"\nSaved model to: {run_dir}")


if __name__ == "__main__":
    main()