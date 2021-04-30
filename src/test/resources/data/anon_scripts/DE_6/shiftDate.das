version "6.2"

a_date := "20200228"
a_shift := "2"
(0008,0012) := shiftDateByIncrement[(0008,0012), "10"]
(0008,0020) := shiftDateByIncrement[a_date, a_shift]
(0008,0021) := shiftDateByIncrement["20210228", a_shift]
(0008,0022) := shiftDateByIncrement[a_date, "-5"]
(0008,0024) := shiftDateByIncrement["20181231", "365"]
