INSERT INTO incidents (id, incident_title, incident_summary, crs, x, y, incident_type, incident_status, timestamp, last_updated)
VALUES
    ('00000000-0000-0000-0000-000000000001',
     'Bridge Fire',
     'Fire reported on main bridge',
     'EPSG:4326', '51.759', '19.457',
     'FIRE', 'OPEN',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    ('00000000-0000-0000-0000-000000000002',
     'Road Accident',
     'Multi-vehicle collision on highway',
     'EPSG:4326', '51.761', '19.460',
     'ACCIDENT', 'IN_PROGRESS',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    ('00000000-0000-0000-0000-000000000003',
     'Flood Warning',
     'River overflow detected near city center',
     'EPSG:4326', '51.755', '19.450',
     'FLOOD', 'ACKNOWLEDGED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    ('00000000-0000-0000-0000-000000000004',
     'Gas Leak',
     'Gas leak reported in residential block',
     'EPSG:4326', '51.762', '19.455',
     'ACCIDENT', 'RESOLVED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    ('00000000-0000-0000-0000-000000000005',
     'Electrical Outage',
     'Power outage affecting northern district',
     'EPSG:4326', '51.770', '19.440',
     'ELECTRICITY', 'ARCHIVED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);