type First record {|
    string val;
|};

type Second record {|
    string a;
    int b;
|};

function testUnionRecordWithUnderscore() {
    First|Second _ = {};
}
