import {
  type DBSchema,
  type IDBPDatabase,
  type IDBPTransaction,
  openDB,
  type StoreNames,
} from "idb";
import type { OutboxEntry, Snapshot } from "./types";

export const OFFLINE_DB_NAME = "city-failure-predictor";

export interface OfflineDbSchema extends DBSchema {
  outbox: { key: number; value: OutboxEntry; indexes: { byOwner: string } };
  snapshots: { key: string; value: Snapshot; indexes: { byOwner: string } };
}

type OfflineDb = IDBPDatabase<OfflineDbSchema>;
type UpgradeTransaction = IDBPTransaction<
  OfflineDbSchema,
  StoreNames<OfflineDbSchema>[],
  "versionchange"
>;
export type Migration = (db: OfflineDb, tx: UpgradeTransaction) => void;

export const MIGRATIONS: readonly Migration[] = [
  (db) => {
    const outbox = db.createObjectStore("outbox", {
      keyPath: "seq",
      autoIncrement: true,
    });
    outbox.createIndex("byOwner", "owner");

    const snapshots = db.createObjectStore("snapshots", { keyPath: "key" });
    snapshots.createIndex("byOwner", "owner");
  },
];

export function openVersionedDb(
  name: string,
  migrations: readonly Migration[],
): Promise<OfflineDb> {
  return openDB<OfflineDbSchema>(name, migrations.length, {
    upgrade(db, oldVersion, _newVersion, tx) {
      for (let version = oldVersion; version < migrations.length; version++) {
        migrations[version](db, tx);
      }
    },
    blocking() {
      void dbPromise?.then((db) => db.close());
      dbPromise = null;
    },
    terminated() {
      dbPromise = null;
    },
  });
}

let dbPromise: Promise<OfflineDb> | null = null;

export function getOfflineDb(): Promise<OfflineDb> {
  dbPromise ??= openVersionedDb(OFFLINE_DB_NAME, MIGRATIONS);
  return dbPromise;
}

export async function closeOfflineDbForTests(): Promise<void> {
  const pending = dbPromise;
  dbPromise = null;
  (await pending)?.close();
}
