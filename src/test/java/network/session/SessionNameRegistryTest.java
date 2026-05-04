package network.session;

public class SessionNameRegistryTest {
    public static void main(String[] args) {
        shouldReserveCommitAndBlockFurtherReservations();
        shouldReleaseReservationWhenJoinFails();
        System.out.println("SessionNameRegistryTest: all tests passed");
    }

    private static void shouldReserveCommitAndBlockFurtherReservations() {
        SessionNameRegistry registry = new SessionNameRegistry();

        require(registry.reserve("alice"), "first reservation should succeed");
        require(!registry.reserve("alice"), "duplicate reservation should fail while pending");
        require(registry.commit("alice"), "commit should succeed for reserved name");
        require(registry.isCommitted("alice"), "name should be committed after commit");
        require(!registry.reserve("alice"), "reservation should fail after commit");
    }

    private static void shouldReleaseReservationWhenJoinFails() {
        SessionNameRegistry registry = new SessionNameRegistry();

        require(registry.reserve("carla"), "reservation should succeed");
        require(registry.release("carla"), "release should remove pending reservation");
        require(registry.reserve("carla"), "name should be available again after release");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
