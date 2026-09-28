// SPDX-FileCopyrightText: 2020 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.terminal;

import dan200.computercraft.core.terminal.Terminal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link TerminalState} round tripping works as expected.
 */
public class TerminalStateTest {
    @RepeatedTest(5)
    public void testRoundTrip() {
        var terminal = randomTerminal();

        var buffer = new FriendlyByteBuf(Unpooled.directBuffer());
        new TerminalState(terminal).write(buffer);

        checkEqual(terminal, read(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    public void testIsSameAsMatchesEncoding() {
        var base = randomTerminal();
        List<TerminalState> states = new ArrayList<>();
        states.add(new TerminalState((NetworkedTerminal) null));
        states.add(new TerminalState(base));
        states.add(new TerminalState(copy(base)));
        states.add(modified(base, t -> t.getLine(2).setChar(3, '!')));
        states.add(modified(base, t -> t.getTextColourLine(1).setChar(0, 'e')));
        states.add(modified(base, t -> t.getBackgroundColourLine(4).setChar(9, 'b')));
        states.add(modified(base, t -> t.setCursorPos(4, 2)));
        states.add(modified(base, t -> t.setCursorBlink(true)));
        states.add(modified(base, t -> t.setTextColour(3)));
        states.add(modified(base, t -> t.setBackgroundColour(7)));
        states.add(modified(base, t -> t.getPalette().setColour(5, 0.5, 0.25, 0.125)));
        states.add(modified(base, t -> t.resize(10, 6)));
        states.add(modified(base, t -> t.resize(5, 10)));

        // Blank colour and non-colour terminals share a buffer, and so only differ by their colour flag.
        states.add(new TerminalState(new NetworkedTerminal(10, 5, true)));
        states.add(new TerminalState(new NetworkedTerminal(10, 5, false)));

        for (var a : states) {
            assertFalse(a.isSameAs(null));
            for (var b : states) {
                assertEquals(
                    Arrays.equals(encode(a), encode(b)), a.isSameAs(b),
                    "isSameAs must agree with the encoded form"
                );
            }
        }

        // Sanity check the test itself: identical terminals produce identical, but distinct, states.
        assertNotSame(states.get(1), states.get(2));
        assertTrue(states.get(1).isSameAs(states.get(2)));
        assertFalse(states.get(1).isSameAs(states.get(3)));
        assertFalse(states.get(states.size() - 2).isSameAs(states.get(states.size() - 1)));
    }

    private static byte[] encode(TerminalState state) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        state.write(buffer);
        var bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static ByteBuf encodeTerminal(NetworkedTerminal terminal) {
        var buffer = Unpooled.buffer();
        terminal.write(new FriendlyByteBuf(buffer));
        return buffer;
    }

    private static NetworkedTerminal copy(NetworkedTerminal terminal) {
        var copy = new NetworkedTerminal(terminal.getWidth(), terminal.getHeight(), terminal.isColour());
        copy.read(new FriendlyByteBuf(encodeTerminal(terminal)));
        return copy;
    }

    private static TerminalState modified(NetworkedTerminal terminal, Consumer<NetworkedTerminal> modify) {
        var copy = copy(terminal);
        modify.accept(copy);
        return new TerminalState(copy);
    }

    private static NetworkedTerminal randomTerminal() {
        var random = new Random();
        var terminal = new NetworkedTerminal(10, 5, true);
        for (var y = 0; y < terminal.getHeight(); y++) {
            var buffer = terminal.getLine(y);
            for (var x = 0; x < buffer.length(); x++) buffer.setChar(x, (char) (random.nextInt(26) + 65));
        }

        return terminal;
    }

    private static void checkEqual(Terminal expected, Terminal actual) {
        assertNotNull(expected, "Expected cannot be null");
        assertNotNull(actual, "Actual cannot be null");
        assertEquals(expected.isColour(), actual.isColour(), "isColour must match");
        assertEquals(expected.getHeight(), actual.getHeight(), "Heights must match");
        assertEquals(expected.getWidth(), actual.getWidth(), "Widths must match");

        for (var y = 0; y < expected.getHeight(); y++) {
            assertEquals(expected.getLine(y).toString(), actual.getLine(y).toString());
        }
    }

    private static NetworkedTerminal read(FriendlyByteBuf buffer) {
        return new TerminalState(buffer).create();
    }
}
