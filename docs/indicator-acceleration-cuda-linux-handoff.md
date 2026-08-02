# Linux CUDA qualification handoff

The shared CUDA implementation now builds from
`ta4j-cli/src/main/native/cuda/` for Windows and Linux x86_64. Linux must reuse
operation ABI 1, RNG version 1, JNI payload ordering, status meanings, kernel
source, golden fixtures, and the `1e-4` FP64 qualification tolerance. Do not
fork a Linux-specific kernel or RNG corpus.

## Work remaining on a Linux CUDA host

1. Build the `cuda-linux-x86_64` classifier and inspect the packaged
   `META-INF/native/linux-x86_64/libta4j-cuda-accelerator.so` plus SHA-256
   sidecar.
2. Validate x86_64 architecture, `ldd` dependencies, rpath, extraction
   permissions, checksum failure, unwritable cache, missing runtime, and wrong
   architecture behavior.
3. Run the unchanged Java parity, failure, memory, and concurrency matrix plus
   CUDA Compute Sanitizer memcheck, racecheck, initcheck, and synccheck.
4. Run five fresh JVMs per benchmark workload and capture distro, kernel,
   glibc, libstdc++, JDK, GCC, CMake, driver, toolkit, GPU, compute capability,
   container runtime, commit, tree, and native checksum.
5. Verify transparent `-Dta4j.acceleration=auto` fallback and acceleration
   diagnostics through a real `BarSeriesManager` backtest.

Start on the target host with:

```bash
scripts/acceleration/linux-cuda-handoff.sh .
./mvnw -pl ta4j-cli -am -Pcuda-linux-x86_64 package
```

Linux support may be advertised only for the exact green environment matrix.
Until that report exists, Linux remains implemented but unqualified and the
combined feature stays in review.
