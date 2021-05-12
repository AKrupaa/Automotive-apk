package arkadiusz.krupinski.automotive3.Util;

public class EngineValuesPWM {

    //     INPUTS
    private final double nJoyX;              // Joystick X input                     (-128..+127)
    private final double nJoyY;              // Joystick Y input                     (-128..+127)

    // OUTPUTS
    private double nMotMixL;           // Motor (left)  mixed output           (-128..+127)
    private double nMotMixR;           // Motor (right) mixed output           (-128..+127)


    private double fPivYLimit = 50.0;

    // TEMP VARIABLES
    private double nMotPremixL;    // Motor (left)  premixed output        (-128..+127)
    private double nMotPremixR;    // Motor (right) premixed output        (-128..+127)
    private double nPivSpeed;      // Pivot Speed                          (-128..+127)
    private double fPivScale;      // Balance scale b/w drive and pivot    (   0..1   )

    public EngineValuesPWM(double x, double y) {
        this.nJoyX = x;
        this.nJoyY = y;
    }


    // Calculate Drive Turn output due to Joystick X input
    public void calulcate() {
        if (nJoyY >= 0) {
            // Forward
            nMotPremixL = (nJoyX >= 0) ? 127.0 : (127.0 + nJoyX);
            nMotPremixR = (nJoyX >= 0) ? (127.0 - nJoyX) : 127.0;
        } else {
            // Reverse
            nMotPremixL = (nJoyX >= 0) ? (127.0 - nJoyX) : 127.0;
            nMotPremixR = (nJoyX >= 0) ? 127.0 : (127.0 + nJoyX);
        }

// Scale Drive output due to Joystick Y input (throttle)
        nMotPremixL = nMotPremixL * nJoyY / 128.0;
        nMotPremixR = nMotPremixR * nJoyY / 128.0;

// Now calculate pivot amount
// - Strength of pivot (nPivSpeed) based on Joystick X input
// - Blending of pivot vs drive (fPivScale) based on Joystick Y input
        nPivSpeed = nJoyX;
        fPivScale = (Math.abs(nJoyY) > fPivYLimit) ? 0.0 : (1.0 - Math.abs(nJoyY) / fPivYLimit);

// Calculate final mix of Drive and Pivot
        nMotMixL = (1.0 - fPivScale) * nMotPremixL + fPivScale * (nPivSpeed);
        nMotMixR = (1.0 - fPivScale) * nMotPremixR + fPivScale * (-nPivSpeed);
    }

    public double getnMotMixL() {
        return nMotMixL;
    }

    public double getnMotMixR() {
        return nMotMixR;
    }

    public byte getRight() {
        return (byte) nMotMixL;
    }

    public byte getLeft() {
        return (byte) nMotMixR;
    }

    public byte getRightU2() {
        byte u2 = 0;

        if (nMotMixR >= 0) {
            u2 = (byte) nMotMixR;
        } else {
            u2 = (byte) Math.abs(nMotMixR);
            u2 = (byte) ~u2;
            u2 += 1;
        }

        return u2;
    }

    public byte getLeftU2() {
        byte u2 = 0;

        if (nMotMixL >= 0) {
            u2 = (byte) nMotMixL;
        } else {
            u2 = (byte) Math.abs(nMotMixL);
            u2 = (byte) ~u2;
            u2 += 1;
        }

        return u2;
    }

    public String getHexLeftValue() {

        boolean negative_l = this.nMotMixL < 0;
//        boolean negative_r = engineRight < 0;

        int engineLeft_i;
        int engineRight_i;

        if (negative_l) {
            engineLeft_i = (int) -nMotMixL;
        } else {
            engineLeft_i = (int) nMotMixL;
        }

//        if (negative_r) {
//            engineRight_i = (int) -engineRight;
//        } else {
//            engineRight_i = (int) engineRight;
//        }

//        Log.i("Engine left:", Integer.toString(engineLeft_i));
//        Log.i("Engine right", Integer.toString(engineRight_i));

        if (negative_l) {
            engineLeft_i += 128;
        }
//        if (negative_r) {
//            engineRight_i += 128;
//        }

        String l = Integer.toHexString(engineLeft_i);

        if (l.length() == 1) {
            l = "0" + l;
        }

//        String r = Integer.toHexString(engineRight_i);
//        if (r.length() == 1) {
//            r = "0" + r;
//        }

        return l;
    }

    public byte[] getHexRightValue() {
//        boolean negative_l = engineLeft < 0;
        boolean negative_r = this.nMotMixR < 0;

        int engineLeft_i;
        int engineRight_i;

//        if (negative_l) {
//            engineLeft_i = (int) -engineLeft;
//        } else {
//            engineLeft_i = (int) engineLeft;
//        }

        if (negative_r) {
            engineRight_i = (int) -this.nMotMixR;
        } else {
            engineRight_i = (int) this.nMotMixR;
        }

//        Log.i("Engine left:", Integer.toString(engineLeft_i));
//        Log.i("Engine right", Integer.toString(engineRight_i));

//        if (negative_l) {
//            engineLeft_i += 128;
//        }
        if (negative_r) {
            engineRight_i += 128;
        }

//        String l = Integer.toHexString(engineLeft_i);

//        if (l.length() == 1) {
//            l = "0" + l;
//        }

        String r = Integer.toHexString(engineRight_i);
        if (r.length() == 1) {
            r = "0" + r;
        }

        return r.getBytes();
    }
}
