#!/usr/bin/env python3
"""Locate DEX methods that directly reference selected string constants."""

from __future__ import annotations

import argparse
from pathlib import Path

from androguard.core.dex import DEX
from loguru import logger


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("dex_dir", type=Path)
    parser.add_argument("terms", nargs="+")
    parser.add_argument(
        "--require-all",
        action="store_true",
        help="only report methods that reference every requested term",
    )
    parser.add_argument(
        "--summary",
        action="store_true",
        help="print matching method signatures without instruction context",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    logger.remove()
    needles = tuple(term.casefold() for term in args.terms)

    for dex_path in sorted(args.dex_dir.glob("classes*.dex")):
        dex = DEX(dex_path.read_bytes())
        for method in dex.get_encoded_methods():
            code = method.get_code()
            if code is None:
                continue
            instructions = list(code.get_bc().get_instructions())
            outputs = [instruction.get_output() for instruction in instructions]
            matched = {
                term
                for term in needles
                if any(term in output.casefold() for output in outputs)
            }
            if not matched or (args.require_all and len(matched) != len(needles)):
                continue
            print(
                f"{dex_path.name}\t{method.get_class_name()}->"
                f"{method.get_name()}{method.get_descriptor()}\t"
                f"{','.join(sorted(matched))}"
            )
            if args.summary:
                continue
            for index, output in enumerate(outputs):
                if any(term in output.casefold() for term in matched):
                    start = max(0, index - 5)
                    end = min(len(outputs), index + 10)
                    for line in outputs[start:end]:
                        print(f"    {line}")
                    print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
