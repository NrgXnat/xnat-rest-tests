# snapshot_codecs

One study, five series, for `TestSnapshotCodecs`. Each series puts snapshot generation on a
different path, and they share a study so a single import covers all of them.

| # | series | SOP class | transfer syntax | what it covers |
|---|--------|-----------|-----------------|----------------|
| 1 | `EVLE_CONTROL` | MR Image Storage | `1.2.840.10008.1.2.1` | control — needs no codec at all |
| 2 | `JPEG2000_LOSSLESS` | MR Image Storage | `1.2.840.10008.1.2.4.90` | the codec that failed in production |
| 3 | `RLE_LOSSLESS` | MR Image Storage | `1.2.840.10008.1.2.5` | RLE, now decoded by dcm4che 5 rather than dcm4che 2 |
| 4 | `MULTIFRAME_EVLE` | Enhanced MR Image Storage | `1.2.840.10008.1.2.1` | 8 frames in 1 file — slice count is not file count |
| 5 | `SECONDARY_CAPTURE` | Secondary Capture Image Storage | `1.2.840.10008.1.2.1` | lands in the secondary catalog, which has no frame count |

Series 1 is the reason the others are readable as evidence. A compressed series failing on its own
could mean the codecs are missing or that snapshot generation is broken outright, and those want
different fixes; a control that renders regardless says which.

Series 4 and 5 are about the catalog rather than the codec. `CatalogBuilder` records `dimensions_z`
only on the primary catalog, and `SOPModel.isPrimaryImagingSOP` decides which catalog a series lands
in from its SOP class against `primary-sops.txt`. MR and Enhanced MR are in that list; Secondary
Capture is not, so with `separateSecondaryDicomOnArchive` on — the default — series 5 lands in the
secondary catalog with no frame count at all. Snapshot generation used to refuse that case outright
and now counts frames off the objects instead. Series 4 separates "number of slices" from "number of
files", which are equal for every other series here and so would otherwise never be distinguished.

## Provenance

256x256 MONOCHROME2, 16-bit except series 5 which is 8-bit. Subject `SNAPSHOT_TEST_001`, session
`SNAPSHOT_CODECS` via `PatientComments`, which is what XNAT's default identifier reads, so the
archived session lands at a known address rather than one derived per deployment.

Pixels are computed from a formula — a diagonal gradient, concentric rings, and a bright bar whose
row tracks the instance or frame — so a montage reads as a staircase and a snapshot that renders
wrongly, blank, or out of order is obvious by eye. **There is no patient data here**; nothing
derives from a real study.

Series 1, 2, 4 and 5 are generated with dcm4che, transcoding to the target syntax where one is
needed. That needs `dcm4che-imageio-opencv` and the matching OpenCV native on `java.library.path`;
see `docs/mirroring-opencv-codecs.md` in the xnat repository for where those come from.

Series 3 is generated uncompressed and then converted with DCMTK's `dcmcrle`, because **dcm4che
ships an RLE reader but no RLE writer** — true of dcm4che 2 as well, so this is not something the
upgrade took away. `ImageWriterFactory.properties` has no entry for `1.2.840.10008.1.2.5` and
`dcm4che-imageio-rle` registers only an `ImageReaderSpi`. That is also why `alterPixels` on an RLE
object writes Explicit VR Little Endian back rather than RLE: `EncapsulatedPixelRedactor` finds no
writer and falls back to uncompressed. RLE is lossless, so nothing is lost and no lossy compression
history is recorded.
