# EtsyConnector
EtsyConnector is a sample program to retrieve a shops listings from etsy.com and compare the current listing with the previous retrieved listing.
The output looks like this:

```
Shop ID 234234
- removed listing 234987 "Cool cat shirt"
+ added listing 98743598 "Cooler cat shirt"

Shop ID 9875
No Changes since last sync

Shop 93580
+ added listing 3094583 "Artisanal cheese"
```
### Usage
```
Usage: command <ShopIds> <Listings>

  ShopIds  - The fully qualified name of input data file of Shop Ids or "" to use stdin.
             Each line of the input file consists of a Shop Id. For example "1598799"
  Listings - Name of an existing output directory containing shop listings. The default is "/var/tmp/listings".
```
For example: $ etsyConn shopids

#### Build Notes
This is a java 1.8 project built using gradle 4.0. Clone the repo into your local environment and you will be able to run the following gradle commands from the home directory of the project. 

To build:
```
./gradlew build
```
To run from build environment:
```
./gradlew install
./build/install/etsyConn/bin/etsyConn ""
```
