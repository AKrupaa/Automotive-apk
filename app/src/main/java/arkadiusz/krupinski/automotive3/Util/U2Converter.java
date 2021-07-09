package arkadiusz.krupinski.automotive3.Util;

public class U2Converter {

    public byte toU2(int integer) {
        byte u2 = 0;

        if (integer >= 0) {
            u2 = (byte) integer;
        } else {
            u2 = (byte) Math.abs(integer);
            u2 = (byte) ~u2;
            u2 += 1;
        }

        return u2;
    }
}
