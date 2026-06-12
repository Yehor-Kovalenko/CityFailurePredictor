import glob

import pandas as pd
import logging

class LondonSingleHouseLoader:

    def __init__(self, folder_path: str):
        self.folder_path = folder_path

    def _clean(self, df: pd.DataFrame) -> pd.DataFrame:

        print("Starting data cleaning...")

        # strip columns
        df.columns = df.columns.str.strip()

        # remove invalid values
        before = len(df)
        df = df[df["KWH/hh (per half hour)"] != "Null"]
        after = len(df)

        print(f"Removed 'Null' rows: {before - after}")

        # convert to float
        df["KWH/hh (per half hour)"] = df[
            "KWH/hh (per half hour)"
        ].astype(float)

        print("Converted consumption column to float")

        return df


    def load_houses(self, house_ids):
        """
        Loads one or multiple houses from raw CSV partitions.
        house_ids: str or list[str]
        """

        if isinstance(house_ids, str):
            house_ids = [house_ids]

        house_ids = set(str(h).strip() for h in house_ids)

        print(f"Loading houses: {house_ids}")

        files = glob.glob(f"{self.folder_path}/**/*.csv", recursive=True)
        files = sorted(files)

        print(f"Found {len(files)} CSV files")

        chunks = []

        for i, file in enumerate(files):

            df = pd.read_csv(file)

            # --- safety cleanup (important for consistency)
            df.columns = df.columns.str.strip()

            if "LCLid" not in df.columns:
                continue

            df["LCLid"] = df["LCLid"].astype(str).str.strip()

            df_house = df[df["LCLid"].isin(house_ids)]

            if not df_house.empty:
                chunks.append(df_house)

            if i % 20 == 0:
                print(f"Processed {i}/{len(files)} files")

        if not chunks:
            raise ValueError(f"No data found for houses {house_ids}")

        full_df = pd.concat(chunks, ignore_index=True)

        print(f"Raw merged shape: {full_df.shape}")

        # --- datetime safety
        full_df["DateTime"] = pd.to_datetime(full_df["DateTime"], errors="coerce")
        full_df = full_df.dropna(subset=["DateTime"])

        full_df = full_df.sort_values("DateTime")

        print("Sorting by DateTime completed")

        print(
            f"Date range: {full_df['DateTime'].min()} -> {full_df['DateTime'].max()}"
        )

        print(f"Total readings: {len(full_df)}")

        # your full cleaning logic preserved
        full_df = self._clean(full_df)

        print(f"Final cleaned shape: {full_df.shape}")

        return full_df



# file_path = "/home/agata/Documents/8/a lot of data/Partitioned LCL Data/Small LCL Data/LCL-June2015v2_0.csv"  # one of the 168 files


#
# loader = LondonSingleHouseLoader(
#     folder_path="/home/agata/Documents/8/a lot of data/Partitioned LCL Data/"
# )
#
# df = loader.load_house("MAC000002")
#
# print(df.head())