package io.instanto.sarto.codec.wire;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireCodecTest {
    @Test
    void dispatchesGeneratedBinaryMappings() {
        WireCodec codec = new WireCodec().register(Message.class, new MessageCodec());
        byte[] bytes = codec.encodeContent(new Message(42));
        assertEquals(new Message(42), codec.decodeContent(bytes, Message.class));
        assertTrue(codec.supports("application/vnd.example+protobuf"));
    }

    private record Message(int value) {}

    private static final class MessageCodec implements WireTypeCodec<Message> {
        @Override
        public Class<Message> type() {
            return Message.class;
        }

        @Override
        public byte[] encode(Message value) {
            return ByteBuffer.allocate(Integer.BYTES).putInt(value.value()).array();
        }

        @Override
        public Message decode(byte[] content) {
            return new Message(ByteBuffer.wrap(content).getInt());
        }
    }
}
