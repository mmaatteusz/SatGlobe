package pl.mateusz.satglobe;

import java.util.Locale;

/**
 * Immutable snapshot of one GNSS satellite reported by Android.
 */
public final class SatelliteInfo {
    public static final int CONSTELLATION_UNKNOWN = 0;
    public static final int CONSTELLATION_GPS = 1;
    public static final int CONSTELLATION_SBAS = 2;
    public static final int CONSTELLATION_GLONASS = 3;
    public static final int CONSTELLATION_QZSS = 4;
    public static final int CONSTELLATION_BEIDOU = 5;
    public static final int CONSTELLATION_GALILEO = 6;
    public static final int CONSTELLATION_IRNSS = 7;

    public final int constellation;
    public final int svid;
    public final float cn0DbHz;
    public final float elevationDegrees;
    public final float azimuthDegrees;
    public final boolean usedInFix;
    public final boolean hasAlmanac;
    public final boolean hasEphemeris;
    public final Float carrierFrequencyHz;

    public SatelliteInfo(
            int constellation,
            int svid,
            float cn0DbHz,
            float elevationDegrees,
            float azimuthDegrees,
            boolean usedInFix,
            boolean hasAlmanac,
            boolean hasEphemeris,
            Float carrierFrequencyHz
    ) {
        this.constellation = constellation;
        this.svid = svid;
        this.cn0DbHz = cn0DbHz;
        this.elevationDegrees = elevationDegrees;
        this.azimuthDegrees = azimuthDegrees;
        this.usedInFix = usedInFix;
        this.hasAlmanac = hasAlmanac;
        this.hasEphemeris = hasEphemeris;
        this.carrierFrequencyHz = carrierFrequencyHz;
    }

    public String key() {
        return constellation + ":" + svid;
    }

    public String shortLabel() {
        return String.format(Locale.US, "%s%02d", constellationPrefix(), svid);
    }

    public String constellationName() {
        switch (constellation) {
            case CONSTELLATION_GPS:
                return "GPS";
            case CONSTELLATION_SBAS:
                return "SBAS";
            case CONSTELLATION_GLONASS:
                return "GLONASS";
            case CONSTELLATION_QZSS:
                return "QZSS";
            case CONSTELLATION_BEIDOU:
                return "BeiDou";
            case CONSTELLATION_GALILEO:
                return "Galileo";
            case CONSTELLATION_IRNSS:
                return "NavIC";
            default:
                return "GNSS";
        }
    }

    public String constellationPrefix() {
        switch (constellation) {
            case CONSTELLATION_GPS:
                return "G";
            case CONSTELLATION_SBAS:
                return "S";
            case CONSTELLATION_GLONASS:
                return "R";
            case CONSTELLATION_QZSS:
                return "J";
            case CONSTELLATION_BEIDOU:
                return "C";
            case CONSTELLATION_GALILEO:
                return "E";
            case CONSTELLATION_IRNSS:
                return "I";
            default:
                return "U";
        }
    }

    public int colorArgb() {
        switch (constellation) {
            case CONSTELLATION_GPS:
                return 0xFF53E6B3;
            case CONSTELLATION_GLONASS:
                return 0xFF5CA9FF;
            case CONSTELLATION_GALILEO:
                return 0xFF62E7FF;
            case CONSTELLATION_BEIDOU:
                return 0xFFF277C6;
            case CONSTELLATION_QZSS:
                return 0xFFFFC765;
            case CONSTELLATION_SBAS:
                return 0xFFA9B8C9;
            case CONSTELLATION_IRNSS:
                return 0xFFFF8D6B;
            default:
                return 0xFFD8E0EA;
        }
    }

    public float[] colorRgba(float alpha) {
        int color = colorArgb();
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                alpha
        };
    }
}
