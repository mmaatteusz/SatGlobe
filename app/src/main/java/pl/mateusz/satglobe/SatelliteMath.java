package pl.mateusz.satglobe;

/**
 * Coordinate transforms used by the renderer. Earth is represented as a unit
 * sphere with +Y at the north pole and longitude 0 on +X.
 */
public final class SatelliteMath {
    private SatelliteMath() {
    }

    public static Vec3 earthPoint(double latitudeDegrees, double longitudeDegrees, double radius) {
        double lat = Math.toRadians(latitudeDegrees);
        double lon = Math.toRadians(longitudeDegrees);
        double cosLat = Math.cos(lat);
        return new Vec3(
                radius * cosLat * Math.cos(lon),
                radius * Math.sin(lat),
                radius * cosLat * Math.sin(lon)
        );
    }

    /**
     * Converts Android's clockwise-from-north azimuth and elevation into an
     * Earth-centered direction vector at the receiver.
     */
    public static Vec3 lineOfSightDirection(
            double latitudeDegrees,
            double longitudeDegrees,
            double azimuthDegrees,
            double elevationDegrees
    ) {
        double lat = Math.toRadians(latitudeDegrees);
        double lon = Math.toRadians(longitudeDegrees);
        double az = Math.toRadians(azimuthDegrees);
        double el = Math.toRadians(elevationDegrees);

        Vec3 east = new Vec3(-Math.sin(lon), 0.0, Math.cos(lon));
        Vec3 north = new Vec3(
                -Math.sin(lat) * Math.cos(lon),
                Math.cos(lat),
                -Math.sin(lat) * Math.sin(lon)
        );
        Vec3 up = earthPoint(latitudeDegrees, longitudeDegrees, 1.0);

        double horizontal = Math.cos(el);
        return east.scale(Math.sin(az) * horizontal)
                .add(north.scale(Math.cos(az) * horizontal))
                .add(up.scale(Math.sin(el)))
                .normalized();
    }

    /**
     * Intersects the local line of sight with a visual satellite shell. This
     * preserves azimuth/elevation but intentionally compresses orbital scale.
     */
    public static Vec3 satellitePoint(
            double latitudeDegrees,
            double longitudeDegrees,
            double azimuthDegrees,
            double elevationDegrees,
            double shellRadius
    ) {
        if (shellRadius <= 1.0) {
            throw new IllegalArgumentException("shellRadius must be greater than Earth radius");
        }
        Vec3 receiver = earthPoint(latitudeDegrees, longitudeDegrees, 1.015);
        Vec3 direction = lineOfSightDirection(
                latitudeDegrees,
                longitudeDegrees,
                azimuthDegrees,
                elevationDegrees
        );

        double projection = receiver.dot(direction);
        double discriminant = projection * projection
                + shellRadius * shellRadius
                - receiver.dot(receiver);
        double distance = -projection + Math.sqrt(Math.max(0.0, discriminant));
        return receiver.add(direction.scale(distance));
    }

    public static final class Vec3 {
        public final double x;
        public final double y;
        public final double z;

        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 scale(double factor) {
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public double dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public double length() {
            return Math.sqrt(dot(this));
        }

        public Vec3 normalized() {
            double length = length();
            if (length == 0.0) {
                return new Vec3(0.0, 0.0, 0.0);
            }
            return scale(1.0 / length);
        }

        public float[] toFloatArray() {
            return new float[]{(float) x, (float) y, (float) z};
        }
    }
}
