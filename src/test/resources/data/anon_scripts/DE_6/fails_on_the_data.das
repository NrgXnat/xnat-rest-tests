version "6.3"
// An inline script that parses, and passes Batch Transfer's script checks, but fails on every file: the substring runs
// past the end of any PatientName shorter than 999 characters. TestImport and TestBatchTransferAnonymization (AN-12).
(0008,0080) := substring[(0010,0010), 0, 999]
