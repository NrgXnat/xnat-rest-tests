version "6.6"
preBlank := collectValues[(0008,9207)]
(0008,9208) := concatenate["WAS ", (0008,9207)]
blankValues[preBlank]