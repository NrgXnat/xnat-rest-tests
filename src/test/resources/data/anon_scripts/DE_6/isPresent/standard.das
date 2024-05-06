version "6.7"

isPresent[(0008,0070)] ? (0008,1080) := "CORRECT" : (0008,1080) := "INCORRECT"
if (isPresent[(0008,0061)]) {
    (0008,1090) := "INCORRECT"
} else {
    (0008,1090) := "CORRECT"
}