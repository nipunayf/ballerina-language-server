import ballerina/io;

public function checkNumber(int num) {
    if num > 0 {
        io:println("Number is positive.");
    } else {
        io:println("Number is non-positive.");
    }
}
