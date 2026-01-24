#!/bin/bash
set -euo pipefail

repo_dir="${CI_PRIMARY_REPOSITORY_PATH}"
jdk_dir="${CI_DERIVED_DATA_PATH}/JDK"
jdk_version="20.0.1"

echo "repo_dir=$repo_dir"
echo "jdk_dir=$jdk_dir"

if [ "$(uname -m)" = "arm64" ]; then
  arch_type="macos-aarch64"
else
  arch_type="macos-x64"
fi

detect_loc="${jdk_dir}/.${jdk_version}.${arch_type}"
if [ -f "$detect_loc" ]; then
  echo "JDK already installed, skipping"
  exit 0
fi

tar_name="jdk-${jdk_version}_${arch_type}_bin.tar.gz"
url="https://download.oracle.com/java/20/archive/${tar_name}"

echo "Downloading $url"
curl -fL --retry 3 --retry-delay 2 -o "/tmp/${tar_name}" "$url"

rm -rf "${repo_dir}/jdk-${jdk_version}.jdk"
tar -xzf "/tmp/${tar_name}" -C "$repo_dir"

rm -rf "$jdk_dir"
mkdir -p "$jdk_dir"
mv "${repo_dir}/jdk-${jdk_version}.jdk/Contents/Home" "${jdk_dir}/Home"

rm -rf "${repo_dir}/jdk-${jdk_version}.jdk"
touch "$detect_loc"

echo "Installed JDK at ${jdk_dir}/Home"
