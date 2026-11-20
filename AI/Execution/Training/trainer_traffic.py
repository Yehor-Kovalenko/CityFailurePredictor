from pathlib import Path

import pandas as pd

from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder
from AI.Domain.Datasets.traffic_dataset import TrafficDataset
from AI.Execution.Training.common import (
    build_model,
    evaluate,
    load_config,
    parse_args,
    print_scores,
    require_rows,
    save_run,
)


# ----------------------------
# MAIN
# ----------------------------
def main(argv=None):

    args = parse_args(
        description="Train the traffic forecasting model",
        task="traffic",
        data_env="CFP_TRAFFIC_CSV",
        data_help="Path to traffic.csv (columns: DateTime, Junction, Vehicles)",
        argv=argv,
    )

    config = load_config(args.config)

    model_type = config["model"]["type"].lower()
    window_size = config["features"]["window_size"]

    train_end = pd.to_datetime(config["split"]["train_end"])
    val_end = pd.to_datetime(config["split"]["val_end"])
    test_end = pd.to_datetime(config["split"]["test_end"])

    # ----------------------------
    # MODEL
    # ----------------------------
    model = build_model(model_type)

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

    dataset = TrafficDataset(args.data_path)

    df = dataset.load()

    junction_ids = sorted(int(j) for j in df["Junction"].unique())

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
        window_size=window_size,
        use_hour=config["features"]["use_hour"],
        use_day_of_week=config["features"]["use_day_of_week"],
        junction_ids=junction_ids,
    )

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
    # TRAIN
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
        task_weights_dir="traffic_weights",
        model_type=model_type,
        metadata={
            "task": "traffic",
            "config": config,
            "data_source": Path(args.data_path).name,
            "junction_ids": junction_ids,
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
