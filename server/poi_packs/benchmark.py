"""Measure local SQLite search; results are not Karoo performance claims."""
import argparse
import json
import math
import sqlite3
import statistics
import time
from pathlib import Path

from packs import fts_query


def nearby(db, lat, lon, radius_km=5):
    # Sweden prototype: bounding boxes crossing poles/dateline need a split query.
    delta_lat = math.degrees(radius_km / 6371.0088)
    delta_lon = delta_lat / math.cos(math.radians(lat))
    rows = db.execute("""SELECT p.osm_id,p.lat,p.lon FROM poi_geo g
        JOIN poi p ON p.id=g.id WHERE g.min_lon<=? AND g.max_lon>=?
        AND g.min_lat<=? AND g.max_lat>=?""",
        (lon+delta_lon, lon-delta_lon, lat+delta_lat, lat-delta_lat)).fetchall()
    result = []
    for identity, plat, plon in rows:
        a = (math.sin(math.radians(plat-lat)/2)**2 + math.cos(math.radians(lat)) *
             math.cos(math.radians(plat)) * math.sin(math.radians(plon-lon)/2)**2)
        distance = 6371.0088 * 2 * math.asin(math.sqrt(min(1, a)))
        if distance <= radius_km:
            result.append((distance, identity))
    return sorted(result)


def benchmark(database, iterations=100):
    db = sqlite3.connect(f'{database.resolve().as_uri()}?mode=ro', uri=True)
    report = {'poi_count': db.execute('SELECT count(*) FROM poi').fetchone()[0], 'queries': {}}
    try:
        for term in ('water', 'coffee', 'bike repair', 'wc', 'malmo'):
            expression = fts_query(term)
            times = []
            for _ in range(iterations):
                start = time.perf_counter()
                rows = db.execute('SELECT p.osm_id,p.name FROM poi_search s JOIN poi p ON p.id=s.docid WHERE s.search_text MATCH ? LIMIT 50', (expression,)).fetchall()
                times.append((time.perf_counter() - start) * 1000)
            report['queries'][term] = {'returned': len(rows), 'median_ms': round(statistics.median(times), 3), 'max_ms': round(max(times), 3)}
        times = []
        for _ in range(20):
            start = time.perf_counter()
            matches = nearby(db, 55.605, 13.004)
            times.append((time.perf_counter() - start) * 1000)
        report['malmo_5km_spatial_search'] = {
            'matches': len(matches), 'median_ms': round(statistics.median(times), 3),
            'max_ms': round(max(times), 3), 'includes_distance_sort': True}
    finally:
        db.close()
    return report


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('database', type=Path)
    print(json.dumps(benchmark(p.parse_args().database), indent=2))
