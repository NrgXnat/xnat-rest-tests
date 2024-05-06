version "6.7"
true[] ? (0018,9758) := "YEP" : (0018,9758) := "NOPE"
false[] ? (0018,9759) := "NOPE" : (0018,9759) := "YEP"
if (true[]) {
    (0018,9760) := "YEP"
} else {
    (0018,9760) := "NOPE"
}
if (false[]) {
    (0018,9761) := "NOPE"
} else {
    (0018,9761) := "YEP"
}