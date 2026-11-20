from pathlib import Path

import pandas as pd

from AI.Domain.Datasets.feature_builder_london_house import FeatureLondonHouseBuilder
from AI.Domain.Datasets.london_house_loader import LondonSingleHouseLoader
from AI.Execution.Training.common import (
    REPO_ROOT,
    build_model,
    evaluate,
    load_config,
    parse_args,
    print_scores,
    require_rows,
    save_run,
)


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
def main(argv=None):

    args = parse_args(
        description="Train the electricity consumption forecasting model",
        task="electricity",
        data_env="CFP_LCL_DATA_DIR",
        data_help="Folder with the Low Carbon London CSV partitions",
        data_default=str(REPO_ROOT / "datasets" / "sample"),
        argv=argv,
    )

    config = load_config(args.config)

    validate_config(config)

    house_ids = resolve_house_ids(config)
    model_type = config["model"]["type"].lower()

    window_size = config["features"]["window_size"]

    model = build_model(model_type)

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
    loader = LondonSingleHouseLoader(folder_path=args.data_path)

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

    for name, X in (("train", X_train), ("validation", X_val), ("test", X_test)):
        require_rows(name, X)

    # ----------------------------
    # MODEL
    # ----------------------------

    print(f"\nTraining {model_type}...")
    model.train(X_train, y_train)

    if hasattr(model, "calibrate"):
        model.calibrate(X_val, y_val)

    # ----------------------------
    # EVALUATION
    # ----------------------------
    val_scores = evaluate(model, X_val, y_val, last_value_column=window_size - 1)
    test_scores = evaluate(model, X_test, y_test, last_value_column=window_size - 1)

    print_scores("VALIDATION", val_scores)
    print_scores("TEST", test_scores)

    # ----------------------------
    # SAVE
    # ----------------------------
    run_dir = save_run(
        model,
        task_weights_dir="electricity_weights",
        model_type=model_type,
        metadata={
            "task": "electricity",
            "config": config,
            "data_source": Path(args.data_path).name,
            "house_ids": house_ids,
            "data_range": [df["DateTime"].min(), df["DateTime"].max()],
            "validation": val_scores,
            "test": test_scores,
        },
        output_dir=args.output_dir,
    )

    print(f"\nSaved model to: {run_dir}")

    return run_dir


if __name__ == "__main__":
    main()
