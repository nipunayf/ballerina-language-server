type Person record {|
    string name;
    int age;
|};

function testNormalRecordWithUnderscore() {
    Person _ = {};
}
