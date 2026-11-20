from pathlib import Path

import pandas as pd

from AI.Domain.Datasets.feature_builder_london_house import FeatureLondonHouseBuilder
from AI.Domain.Datasets.london_house_loader import LondonSingleHouseLoader
from AI.Execution.Training.common import (
    REPO_ROOT,
    fit_evaluate_save,
    load_train_config,
    parse_args,
    split_by_time,
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
# TRAINING
# ----------------------------
def run(config, data_path, output_dir=None):

    validate_config(config)

    house_ids = resolve_house_ids(config)
    model_type = config["model"]["type"].lower()
    window_size = config["features"]["window_size"]

    print("\n=== CONFIG ===")
    print(f"Houses: {house_ids}")
    print(f"Model: {model_type}")
    print(f"Window: {window_size}")

    print("\nLoading data...")
    df = LondonSingleHouseLoader(folder_path=data_path).load_houses(house_ids=house_ids).copy()

    df["DateTime"] = pd.to_datetime(df["DateTime"])
    df = df.sort_values(["LCLid", "DateTime"])

    print(f"Loaded: {df.shape}")

    builder = FeatureLondonHouseBuilder(window_size=window_size)

    return fit_evaluate_save(
        model_type=model_type,
        splits=[builder.transform(part) for part in split_by_time(df, config["split"])],
        window_size=window_size,
        task_weights_dir="electricity_weights",
        metadata={
            "task": "electricity",
            "config": config,
            "data_source": Path(data_path).name,
            "house_ids": house_ids,
            "data_range": [df["DateTime"].min(), df["DateTime"].max()],
        },
        output_dir=output_dir,
    )


def main(argv=None):

    args = parse_args(
        description="Train the electricity consumption forecasting model",
        data_env="CFP_LCL_DATA_DIR",
        data_help="Folder with the Low Carbon London CSV partitions",
        data_default=str(REPO_ROOT / "datasets" / "sample"),
        argv=argv,
    )

    return run(load_train_config("electricity"), args.data_path, args.output_dir)


if __name__ == "__main__":
    main()
