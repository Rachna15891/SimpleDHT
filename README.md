# SimpleDHT
Android based Distributed Hash Table

This application is a simple DHT based on Chord. Although the design is based on Chord, it is a simplified version of Chord. There is no support of finger tables and finger-based routing. Also node leaves/failures are not handled.

Therefore, there are three things which have been implemented:

    - ID space partitioning/re-partitioning
    - Ring-based routing
    - Node joins

The content provider implements all DHT functionalities and supports insert query and delete operations. Thus, if you multiple instances of the app are run, all content provider instances form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.

SHA-1 is used as the hash function to generate keys. The following code snippet takes a string and generates a SHA-1 hash as a hexadecimal string. Given two keys, you can use the standard lexicographical string comparison to determine which one is greater in order to determine its position in the ring.
