version "6.3"

shiftDateTimeSequenceByIncrement["86400", "(5200,9230)/(0020,9111)/(0018,9151)"]
shiftDateTimeSequenceByIncrement["-86400", "(5200,9230)/(0020,9111)/(0018,9074)"]
(5200,9230)[0]/(0020,9111)[0]/(0008,002A) := "20200101100000"
shiftDateTimeSequenceByIncrement["10", "(5200,9230)/(0020,9111)/(0008,002A)"]