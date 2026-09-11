#!/usr/bin/env python3
"""Verify the static Feishu seams used by the tablet-login hook."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path

from androguard.core.apk import APK
from androguard.core.dex import DEX
from loguru import logger


DEVICE_INFO_CLASS = (
    "Lcom/ss/android/lark/passport/signinsdk_api/entity/DeviceInfo;"
)
ROM_UTILS_CLASS = "Lcom/larksuite/framework/utils/RomUtils;"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--expected-version")
    return parser.parse_args()


def file_hash(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def method_signature(dex_name: str, method: object) -> str:
    return (
        f"{dex_name}:{method.get_class_name()}->"
        f"{method.get_name()}{method.get_descriptor()}"
    )


def main() -> int:
    args = parse_args()
    logger.remove()

    apk = APK(str(args.apk))
    package_name = apk.get_package()
    version_name = apk.get_androidversion_name()

    device_model_getters: list[str] = []
    rom_model_sources: list[str] = []
    tablet_detectors: list[str] = []
    passport_header_writers: list[str] = []

    with zipfile.ZipFile(args.apk) as archive:
        dex_names = sorted(
            name
            for name in archive.namelist()
            if name.startswith("classes") and name.endswith(".dex")
        )
        for dex_name in dex_names:
            dex = DEX(archive.read(dex_name))
            for method in dex.get_encoded_methods():
                signature = method_signature(dex_name, method)
                class_name = method.get_class_name()
                name = method.get_name()
                descriptor = method.get_descriptor().replace(" ", "")

                if (
                    class_name == DEVICE_INFO_CLASS
                    and name == "getDeviceModel"
                    and descriptor == "()Ljava/lang/String;"
                ):
                    device_model_getters.append(signature)

                code = method.get_code()
                if code is None:
                    continue
                outputs = [
                    instruction.get_output()
                    for instruction in code.get_bc().get_instructions()
                ]
                output_text = "\n".join(outputs)

                if (
                    class_name == ROM_UTILS_CLASS
                    and name == "d"
                    and descriptor == "()Ljava/lang/String;"
                    and "Landroid/os/Build;->MODEL" in output_text
                ):
                    rom_model_sources.append(signature)

                folded = output_text.casefold()
                if (
                    "ro.build.characteristics" in folded
                    and "tablet" in folded
                ):
                    tablet_detectors.append(signature)
                if "x-terminal-type" in folded and "x-device-info" in folded:
                    passport_header_writers.append(signature)

    errors: list[str] = []
    if package_name != "com.ss.android.lark":
        errors.append(f"unexpected package: {package_name}")
    if args.expected_version and version_name != args.expected_version:
        errors.append(
            f"unexpected version: {version_name}, expected {args.expected_version}"
        )
    if len(device_model_getters) != 1:
        errors.append(
            "expected one DeviceInfo.getDeviceModel candidate, "
            f"found {len(device_model_getters)}"
        )
    if len(rom_model_sources) != 1:
        errors.append(
            f"expected one RomUtils.d Build.MODEL source, found {len(rom_model_sources)}"
        )
    if not tablet_detectors:
        errors.append("no ro.build.characteristics tablet detector found")
    if not passport_header_writers:
        errors.append("no passport header writer found")

    result = {
        "apk": str(args.apk),
        "packageName": package_name,
        "versionName": version_name,
        "versionCode": apk.get_androidversion_code(),
        "sha256": file_hash(args.apk, "sha256"),
        "md5": file_hash(args.apk, "md5"),
        "dexCount": len(dex_names),
        "deviceModelGetters": device_model_getters,
        "romModelSources": rom_model_sources,
        "tabletDetectors": tablet_detectors,
        "passportHeaderWriters": passport_header_writers,
        "errors": errors,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
