file(SHA256 "${INPUT}" checksum)
file(WRITE "${OUTPUT}" "${checksum}\n")
