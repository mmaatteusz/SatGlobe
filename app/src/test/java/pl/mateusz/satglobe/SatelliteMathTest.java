package pl.mateusz.satglobe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SatelliteMathTest {
    private static final double EPSILON = 1e-6;

    @Test
    public void earthPointUsesExpectedAxes() {
        SatelliteMath.Vec3 equatorPrimeMeridian =
                SatelliteMath.earthPoint(0.0, 0.0, 1.0);
        assertEquals(1.0, equatorPrimeMeridian.x, EPSILON);
        assertEquals(0.0, equatorPrimeMeridian.y, EPSILON);
        assertEquals(0.0, equatorPrimeMeridian.z, EPSILON);

        SatelliteMath.Vec3 northPole =
                SatelliteMath.earthPoint(90.0, 123.0, 1.0);
        assertEquals(0.0, northPole.x, EPSILON);
        assertEquals(1.0, northPole.y, EPSILON);
        assertEquals(0.0, northPole.z, EPSILON);
    }

    @Test
    public void eastAtPrimeMeridianPointsTowardPositiveZ() {
        SatelliteMath.Vec3 direction =
                SatelliteMath.lineOfSightDirection(0.0, 0.0, 90.0, 0.0);
        assertEquals(0.0, direction.x, EPSILON);
        assertEquals(0.0, direction.y, EPSILON);
        assertEquals(1.0, direction.z, EPSILON);
    }

    @Test
    public void zenithLandsOnRequestedVisualShell() {
        SatelliteMath.Vec3 point =
                SatelliteMath.satellitePoint(0.0, 0.0, 10.0, 90.0, 1.7);
        assertEquals(1.7, point.length(), EPSILON);
        assertEquals(1.7, point.x, EPSILON);
        assertEquals(0.0, point.y, EPSILON);
        assertEquals(0.0, point.z, EPSILON);
    }

    @Test
    public void horizonDirectionStillIntersectsVisualShell() {
        SatelliteMath.Vec3 point =
                SatelliteMath.satellitePoint(0.0, 0.0, 90.0, 0.0, 1.7);
        assertEquals(1.7, point.length(), EPSILON);
        assertEquals(1.015, point.x, EPSILON);
        assertEquals(0.0, point.y, EPSILON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShellInsideEarth() {
        SatelliteMath.satellitePoint(0.0, 0.0, 0.0, 45.0, 1.0);
    }
}
