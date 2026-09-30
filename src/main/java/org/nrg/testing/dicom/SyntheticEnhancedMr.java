package org.nrg.testing.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.nrg.testing.LocalDataCache;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Large synthetic Enhanced MR objects for the performance tests, generated once into the local data cache so that
 * hundreds of megabytes of test data never enter the repository. Each object is its own series in a single study,
 * uncompressed Explicit VR Little Endian, which XNAT archives as an MR session with one scan per object. Pixel data
 * is streamed to disk one frame at a time, so generating even a large study needs one frame of memory. A port of
 * the multiframe workload of the ingest performance harness in the XNAT repository.
 */
public class SyntheticEnhancedMr {
    private static final String COMPLETION_MARKER = "README.txt";

    private final String identifier;
    private String projectId;
    private int objectCount = 16;
    private int frameCount = 100;
    private int rows = 512;
    private int columns = 512;

    /**
     * @param identifier Names the data's directory in the local data cache. Generated data is reused for as long as
     *                   the directory exists, so give different shapes or projects different identifiers.
     */
    public SyntheticEnhancedMr(final String identifier) {
        this.identifier = identifier;
    }

    /** Sets the Study Description, which the default XNAT identifier routes a C-STORE on, to this project. */
    public SyntheticEnhancedMr routeTo(final String projectId) {
        this.projectId = projectId;
        return this;
    }

    public SyntheticEnhancedMr objects(final int objectCount) {
        this.objectCount = objectCount;
        return this;
    }

    public SyntheticEnhancedMr frames(final int frameCount) {
        this.frameCount = frameCount;
        return this;
    }

    public SyntheticEnhancedMr frameSize(final int rows, final int columns) {
        this.rows = rows;
        this.columns = columns;
        return this;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public Path directory() {
        return LocalDataCache.pathTo(identifier);
    }

    /** Writes the objects unless an earlier run finished writing them. */
    public SyntheticEnhancedMr build() {
        final Path directory = directory();
        final Path marker    = directory.resolve(COMPLETION_MARKER);
        if (Files.exists(marker)) {
            return this;
        }
        try {
            Files.createDirectories(directory);
            // A run that died partway leaves objects of the old study behind; a C-STORE of the directory would mix them in.
            try (Stream<Path> leftovers = Files.list(directory)) {
                for (final Path leftover : (Iterable<Path>) leftovers::iterator) {
                    Files.delete(leftover);
                }
            }
            final String studyInstanceUid = UIDUtils.createUID();
            final byte[] frame            = new byte[rows * columns * 2];
            for (int object = 1; object <= objectCount; object++) {
                write(directory.resolve(String.format("%02d.dcm", object)).toFile(), header(studyInstanceUid, object), frame);
            }
            Files.write(marker, ("Synthetic Enhanced MR objects for the performance tests: " + objectCount + " objects of " + frameCount
                                 + " frames of " + rows + "x" + columns + ". Delete this directory to generate them again.")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to generate the synthetic Enhanced MR objects in " + directory, e);
        }
        return this;
    }

    private void write(final File file, final Attributes dataset, final byte[] frame) throws IOException {
        try (final DicomOutputStream out = new DicomOutputStream(file)) {
            out.writeDataset(dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian), dataset);
            // Pixel data is the dataset's last element, so it can follow the rest a frame at a time.
            out.writeHeader(Tag.PixelData, VR.OW, frameCount * frame.length);
            for (int index = 0; index < frameCount; index++) {
                fill(frame, index);
                out.write(frame, 0, frame.length);
            }
        }
    }

    /** An image-like gradient, different in every frame, of 12-bit values stored little endian in 16 bits. */
    private void fill(final byte[] frame, final int index) {
        int offset = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                final int value = ((index * 13) % 4096 + row + column) & 0x0FFF;
                frame[offset++] = (byte) value;
                frame[offset++] = (byte) (value >> 8);
            }
        }
    }

    /**
     * Minimal but valid Enhanced MR: shared pixel measures and plane orientation, per-frame plane position and frame
     * content. XNAT archives SC Multiframe as secondary resources with no scan, so the object has to be a real MR one.
     */
    private Attributes header(final String studyInstanceUid, final int objectNumber) {
        final Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.EnhancedMRImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "M", "NONE");
        dataset.setString(Tag.ContentDate, VR.DA, "20260101");
        dataset.setString(Tag.ContentTime, VR.TM, "120000");
        dataset.setString(Tag.StudyTime, VR.TM, "120000");
        dataset.setString(Tag.AccessionNumber, VR.SH, "PERF-MULTIFRAME");
        dataset.setString(Tag.Modality, VR.CS, "MR");
        dataset.setString(Tag.InstitutionName, VR.LO, "XNAT performance tests (synthetic)");
        dataset.setString(Tag.ReferringPhysicianName, VR.PN, "Perf^Referrer");
        if (projectId != null) {
            dataset.setString(Tag.StudyDescription, VR.LO, projectId);
        }
        dataset.setString(Tag.SeriesDescription, VR.LO, "multiframe s" + objectNumber);
        dataset.setString(Tag.PatientName, VR.PN, "PERF^Multiframe");
        dataset.setString(Tag.PatientID, VR.LO, "PERF-Multiframe");
        dataset.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUid);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setInt(Tag.SeriesNumber, VR.IS, objectNumber);
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        dataset.setInt(Tag.NumberOfFrames, VR.IS, frameCount);
        dataset.setInt(Tag.Rows, VR.US, rows);
        dataset.setInt(Tag.Columns, VR.US, columns);
        dataset.setInt(Tag.BitsAllocated, VR.US, 16);
        dataset.setInt(Tag.BitsStored, VR.US, 16);
        dataset.setInt(Tag.HighBit, VR.US, 15);
        dataset.setInt(Tag.PixelRepresentation, VR.US, 0);

        final Attributes organization = new Attributes();
        organization.setString(Tag.DimensionOrganizationUID, VR.UI, UIDUtils.createUID());
        dataset.newSequence(Tag.DimensionOrganizationSequence, 1).add(organization);

        final Attributes measures = new Attributes();
        measures.setDouble(Tag.PixelSpacing, VR.DS, 1.0, 1.0);
        measures.setDouble(Tag.SliceThickness, VR.DS, 1.0);
        final Attributes orientation = new Attributes();
        orientation.setDouble(Tag.ImageOrientationPatient, VR.DS, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        final Attributes shared = new Attributes();
        shared.newSequence(Tag.PixelMeasuresSequence, 1).add(measures);
        shared.newSequence(Tag.PlaneOrientationSequence, 1).add(orientation);
        dataset.newSequence(Tag.SharedFunctionalGroupsSequence, 1).add(shared);

        final Sequence perFrame = dataset.newSequence(Tag.PerFrameFunctionalGroupsSequence, frameCount);
        for (int index = 0; index < frameCount; index++) {
            final Attributes position = new Attributes();
            position.setDouble(Tag.ImagePositionPatient, VR.DS, 0.0, 0.0, index);
            final Attributes content = new Attributes();
            content.setString(Tag.StackID, VR.SH, "1");
            content.setInt(Tag.InStackPositionNumber, VR.UL, index + 1);
            content.setInt(Tag.DimensionIndexValues, VR.UL, index + 1);
            final Attributes item = new Attributes();
            item.newSequence(Tag.PlanePositionSequence, 1).add(position);
            item.newSequence(Tag.FrameContentSequence, 1).add(content);
            perFrame.add(item);
        }
        return dataset;
    }
}
