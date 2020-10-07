CREATE TABLE users
(
    username varchar(20) PRIMARY KEY,
    hash     varbinary(100),
    salt     varbinary(100),
    balance  int
);

CREATE TABLE reservations(
    rid int IDENTITY(1,1) PRIMARY KEY,
    username varchar(20),
    fid1 int,
    fid2 int,
    paid bit,
    canceled bit,
    price int,
    CONSTRAINT FK_USERNAME FOREIGN KEY (username)
        REFERENCES users(username)
);

CREATE TABLE capacity(
    fid int FOREIGN KEY REFERENCES flights(fid),
    freeSeat int
);

