from pathlib import Path

from AI.Domain.Datasets.feature_builder_traffic import TrafficFeatureBuilder
from AI.Domain.Datasets.traffic_dataset import TrafficDataset
from AI.Execution.Training.common import (
    fit_evaluate_save,
    load_train_config,
    parse_args,
    split_by_time,
)


# ----------------------------
# TRAINING
# ----------------------------
def run(config, data_path, output_dir=None):

    model_type = config["model"]["type"].lower()
    features = config["features"]

    print("\n=== CONFIG ===")
    print(f"Model: {model_type}")
    print(f"Window: {features['window_size']}")

    print("\nLoading traffic dataset...")
    df = TrafficDataset(data_path).load()

    junction_ids = sorted(int(j) for j in df["Junction"].unique())

    builder = TrafficFeatureBuilder(
        window_size=features["window_size"],
        use_hour=features["use_hour"],
        use_day_of_week=features["use_day_of_week"],
        junction_ids=junction_ids,
    )

    return fit_evaluate_save(
        model_type=model_type,
        splits=[builder.transform(part) for part in split_by_time(df, config["split"])],
        window_size=features["window_size"],
        task_weights_dir="traffic_weights",
        metadata={
            "task": "traffic",
            "config": config,
            "data_source": Path(data_path).name,
            "junction_ids": junction_ids,
            "data_range": [df["DateTime"].min(), df["DateTime"].max()],
        },
        output_dir=output_dir,
    )


def main(argv=None):

    args = parse_args(
        description="Train the traffic forecasting model",
        data_env="CFP_TRAFFIC_CSV",
        data_help="Path to traffic.csv (columns: DateTime, Junction, Vehicles)",
        argv=argv,
    )

    return run(load_train_config("traffic"), args.data_path, args.output_dir)


if __name__ == "__main__":
    main()
