version "6.7"

isPresent[(2001,{Philips Imaging DD 001}0c)] ? (0012,0010) := "CORRECT" : (0012,0010) := "INCORRECT"
isPresent[(2001,{Philips Imaging DD 001}0d)] ? (0012,0020) := "INCORRECT" : (0012,0020) := "CORRECT"
if (isPresent[(2001,{NOPEEEEEEEE}0c)]) {
    (0012,0021) := "INCORRECT"
} else {
    (0012,0021) := "CORRECT"
}