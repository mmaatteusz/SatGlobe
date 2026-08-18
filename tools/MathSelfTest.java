import pl.mateusz.satglobe.SatelliteMath;

public final class MathSelfTest {
    private static void close(double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-6) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        SatelliteMath.Vec3 east =
                SatelliteMath.lineOfSightDirection(0.0, 0.0, 90.0, 0.0);
        close(0.0, east.x);
        close(0.0, east.y);
        close(1.0, east.z);

        SatelliteMath.Vec3 zenith =
                SatelliteMath.satellitePoint(0.0, 0.0, 0.0, 90.0, 1.7);
        close(1.7, zenith.length());
        close(1.7, zenith.x);

        SatelliteMath.Vec3 bydgoszcz =
                SatelliteMath.earthPoint(53.1235, 18.0084, 1.0);
        close(1.0, bydgoszcz.length());

        System.out.println("SatelliteMath self-test: OK");
    }
}
