# Traffic dataset

The data-ingestion service replays every `*.csv` file in this folder as
`traffic.readings.raw` events (the folder is mounted at `/data/traffic`).

Expected columns, one row per junction and hour:

```csv
DateTime,Junction,Vehicles,ID
2015-11-01 00:00:00,1,15,20151101001
```

CSV files are not committed (see `.gitignore`); copy the dataset here before
starting the system. The traffic model was trained on junctions 1-4; readings
of other junctions are ignored by the AI service.

Start the replay with `TRAFFIC_SCHEDULER_ENABLED=true docker compose up -d`,
or call `POST /data-ingestion/traffic/import/next?limit=200` as an admin.
