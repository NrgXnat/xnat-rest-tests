version "6.3"

(0008,002A) := shiftDateTimeByIncrement[(0008,002A), "360"]
(0018,9516) := shiftDateTimeByIncrement["20200101120000.000000", "10"]
(0018,9517) := shiftDateTimeByIncrement["20200101120000.800000", "10"]
(0018,9804) := shiftDateTimeByIncrement["20200228235800", "180"]
(0018,9919) := shiftDateTimeByIncrement["19960808140000", "-600"]