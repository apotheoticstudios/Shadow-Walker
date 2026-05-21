#!/usr/bin/env python3
"""Generate the Shadow Walker dagger item tag from item registry ids."""

from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path


ITEM_ID_RE = re.compile(r"\b[a-z0-9_.-]+:[a-z0-9_./-]+\b")


def add_matching_item_id(raw_id: str, items: set[str]) -> None:
    item_id = raw_id.strip().lower()
    if not ITEM_ID_RE.fullmatch(item_id):
        return

    item_path = item_id.split(":", 1)[1]
    if "dagger" in item_path:
        items.add(item_id)


def read_item_ids_file(path: Path, items: set[str]) -> None:
    for line in path.read_text(encoding="utf-8").splitlines():
        for match in ITEM_ID_RE.findall(line.lower()):
            add_matching_item_id(match, items)


def scan_resource_root(root: Path, items: set[str]) -> None:
    assets = root / "assets"
    if not assets.is_dir():
        return

    for model_file in assets.glob("*/models/item/**/*.json"):
        item_path = model_file.relative_to(model_file.parents[2] / "models" / "item").with_suffix("").as_posix()
        add_matching_item_id(f"{model_file.parents[2].name}:{item_path}", items)


def scan_mod_archive(path: Path, items: set[str]) -> None:
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            parts = Path(name).parts
            if len(parts) < 5 or parts[0] != "assets" or parts[2:4] != ("models", "item"):
                continue
            if not name.endswith(".json"):
                continue

            item_path = Path(*parts[4:]).with_suffix("").as_posix()
            add_matching_item_id(f"{parts[1]}:{item_path}", items)


def scan_mods_dir(path: Path, items: set[str]) -> None:
    for archive_path in sorted(path.glob("*")):
        if archive_path.suffix.lower() not in {".jar", ".zip"}:
            continue
        scan_mod_archive(archive_path, items)


def read_existing_tag(path: Path, items: set[str]) -> None:
    if not path.is_file():
        return

    tag = json.loads(path.read_text(encoding="utf-8"))
    for value in tag.get("values", []):
        if isinstance(value, str):
            items.add(value)
        elif isinstance(value, dict) and isinstance(value.get("id"), str):
            items.add(value["id"])


def write_tag(path: Path, items: set[str], required: bool) -> None:
    values: list[str | dict[str, bool | str]]
    if required:
        values = sorted(items)
    else:
        values = [{"id": item_id, "required": False} for item_id in sorted(items)]

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"replace": False, "values": values}, indent=2) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[1]
    default_output = repo_root / "src/main/resources/data/apotheotics_shadow_walker/tags/items/daggers.json"

    parser = argparse.ArgumentParser(description="Generate the apotheotics_shadow_walker:daggers item tag.")
    parser.add_argument("--items-file", action="append", type=Path, default=[],
                        help="Text file containing item ids, one per line or embedded in registry dump output.")
    parser.add_argument("--resource-root", action="append", type=Path, default=[],
                        help="Resource root to scan, such as src/main/resources or an extracted mod.")
    parser.add_argument("--mods-dir", action="append", type=Path, default=[],
                        help="Directory of mod jar/zip files to scan for item model paths.")
    parser.add_argument("--output", type=Path, default=default_output,
                        help="Tag JSON path to write.")
    parser.add_argument("--no-merge-existing", action="store_true",
                        help="Do not preserve values already present in the output tag.")
    parser.add_argument("--required", action="store_true",
                        help="Write required string entries instead of optional tag entries.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    items: set[str] = set()

    if not args.no_merge_existing:
        read_existing_tag(args.output, items)
    for path in args.items_file:
        read_item_ids_file(path, items)
    for path in args.resource_root:
        scan_resource_root(path, items)
    for path in args.mods_dir:
        scan_mods_dir(path, items)

    write_tag(args.output, items, args.required)
    print(f"Wrote {len(items)} item(s) to {args.output}")


if __name__ == "__main__":
    main()
