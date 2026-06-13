import os
import json
import pandas as pd
from datetime import datetime
from sklearn.metrics import mean_absolute_error

from AI.Domain.Datasets.feature_builder_london_house import FeatureLondonHouseBuilder
from AI.Domain.Datasets.london_house_loader import LondonSingleHouseLoader
from AI.Domain.Models.ngboost_model import NGBoostModel
from AI.Domain.Models.xgboost_model import XGBoostModel


# ----------------------------
# CONFIG VALIDATION
# ----------------------------
def validate_config(config):

    required = {
        "split": ["train_end", "val_end", "test_end"],
        "features": ["window_size"],
        "model": ["type"]
    }

    for section, keys in required.items():
        if section not in config:
            raise ValueError(f"Missing config section: {section}")

        for key in keys:
            if key not in config[section]:
                raise ValueError(f"Missing config key: {section}.{key}")

    if "dataset" not in config:
        raise ValueError("Missing dataset section")

    ds = config["dataset"]

    if "house_id" not in ds and "house_ids" not in ds:
        raise ValueError("dataset must contain house_id or house_ids")


# ----------------------------
# RESOLVE HOUSE IDS
# ----------------------------
def resolve_house_ids(config):
    ds = config["dataset"]

    if "house_ids" in ds:
        house_ids = ds["house_ids"]
    else:
        house_ids = ds["house_id"]

    if isinstance(house_ids, str):
        house_ids = [house_ids]

    return house_ids


# ----------------------------
# MAIN
# ----------------------------
def main():

    config_path = "../../Domain/Resources/Configs/electricity/train_config.json"

    with open(config_path, "r") as f:
        config = json.load(f)

    validate_config(config)

    house_ids = resolve_house_ids(config)
    model_type = config["model"]["type"].lower()

    window_size = config["features"]["window_size"]

    if model_type == "ngboost":
        model = NGBoostModel()

    elif model_type == "xgboost":
        model = XGBoostModel()

    else:
        raise ValueError(f"Unsupported model type: {model_type}")

    train_end = pd.to_datetime(config["split"]["train_end"])
    val_end = pd.to_datetime(config["split"]["val_end"])
    test_end = pd.to_datetime(config["split"]["test_end"])

    print("\n=== CONFIG ===")
    print(f"Houses: {house_ids}")
    print(f"Model: {model_type}")
    print(f"Window: {window_size}")
    print(f"Train: {train_end} | Val: {val_end} | Test: {test_end}")

    # ----------------------------
    # LOAD DATA
    # ----------------------------
    loader = LondonSingleHouseLoader(
        folder_path="/home/agata/Documents/8/a lot of data/Partitioned LCL Data/"
    )

    print("\nLoading data...")
    df = loader.load_houses(house_ids=house_ids).copy()

    df["DateTime"] = pd.to_datetime(df["DateTime"])
    df = df.sort_values(["LCLid", "DateTime"])

    print(f"Loaded: {df.shape}")

    # ----------------------------
    # SPLIT
    # ----------------------------
    train_df = df[df["DateTime"] < train_end]
    val_df = df[(df["DateTime"] >= train_end) & (df["DateTime"] < val_end)]
    test_df = df[(df["DateTime"] >= val_end) & (df["DateTime"] <= test_end)]

    print("\n=== SPLIT ===")
    print(f"Train: {train_df.shape}")
    print(f"Val:   {val_df.shape}")
    print(f"Test:  {test_df.shape}")

    # ----------------------------
    # FEATURES
    # ----------------------------
    builder = FeatureLondonHouseBuilder(window_size=window_size)

    X_train, y_train = builder.transform(train_df)
    X_val, y_val = builder.transform(val_df)
    X_test, y_test = builder.transform(test_df)

    print("\n=== FEATURES ===")
    print(f"Train: {X_train.shape}")
    print(f"Val:   {X_val.shape}")
    print(f"Test:  {X_test.shape}")

    # ----------------------------
    # MODEL
    # ----------------------------

    print(f"\nTraining {model_type}...")
    model.train(X_train, y_train)

    # ----------------------------
    # VALIDATION
    # ----------------------------
    val_preds = model.predict(X_val)
    val_mae = mean_absolute_error(y_val, val_preds)

    print("\n=== VALIDATION ===")
    print(f"MAE: {val_mae:.5f}")

    # ----------------------------
    # TEST
    # ----------------------------
    test_preds = model.predict(X_test)
    test_mae = mean_absolute_error(y_test, test_preds)

    print("\n=== TEST ===")
    print(f"MAE: {test_mae:.5f}")

    # ----------------------------
    # SAVE
    # ----------------------------
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")

    run_dir = os.path.join(
        "../../Domain/Resources/ModelWeights/electricity_weights",
        model_type,
        timestamp
    )

    os.makedirs(run_dir, exist_ok=True)

    model.save(os.path.join(run_dir, "model.joblib"))

    print(f"\nSaved model to: {run_dir}")


if __name__ == "__main__":
    main()