import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.google.common.base.Strings.repeat;
import static java.lang.System.nanoTime;
import static java.util.stream.Collectors.summarizingLong;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.iterate;

public final class Timing implements Serializable {

    private static final long serialVersionUID = 1L;

    private final AtomicReference<Element> head = new AtomicReference<>(null);

    public Timing append(String id) {
        return append(id, null);
    }

    public Timing append(String id, String parentId) {
        return append(new Entry(id, parentId, nanoTime()));
    }

    public Timing append(Entry entry) {
        Element expected = head.get();
        while (!head.compareAndSet(expected, new Element(entry, expected))) {
            expected = head.get();
        }
        return this;
    }

    public Timing append(Timing other, String parentId) {
        Element current = reversed(other.head.get());
        while (current != null) {
            append(current.entry.withParent(parentId));
            current = current.tail;
        }
        return this;
    }

    public Stream<Entry> stream() {
        return iterate(reversed(head.get()), Objects::nonNull, e -> e.tail).map(e -> e.entry);
    }

    private static Element reversed(Element current) {
        Element reversed = null;
        while (current != null) {
            reversed = new Element(current.entry, reversed);
            current = current.tail;
        }
        return reversed;
    }

    public final static class Entry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String id;
        private final String parentId;
        private final long timestamp;

        public Entry(String id, String parentId, long timestamp) {
            this.id = id;
            this.parentId = parentId;
            this.timestamp = timestamp;
        }

        public String getId() {
            return id;
        }

        public String getParentId() {
            return parentId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Entry withParent(String parentId) {
            return new Entry(id, parentId, timestamp);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("parentId", parentId)
                    .add("timestamp", timestamp)
                    .toString();
        }
    }

    private final static class Element implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Entry entry;
        private final Element tail;

        public Element(Entry entry, Element tail) {
            this.entry = entry;
            this.tail = tail;
        }
    }

    public final static class StageBuilder {
        private final Map<Map.Entry<String, String>, String> mapping = new HashMap<>();

        public Stage build(Timing timing, String rootId) {
            long duration = timing.head.get().entry.timestamp - reversed(timing.head.get()).entry.timestamp;
            return build(timing, rootId, duration, null);
        }

        public Stage build(Timing timing) {
            return build(timing, "root");
        }

        public To map(String entryId1, String entryId2) {
            return new To(entryId1, entryId2);
        }

        public class To {
            private final String entryId1;
            private final String entryId2;

            public To(String entryId1, String entryId2) {
                this.entryId1 = entryId1;
                this.entryId2 = entryId2;
            }

            public StageBuilder to(String stageId) {
                mapping.put(new SimpleImmutableEntry<>(entryId1, entryId2), stageId);
                return StageBuilder.this;
            }
        }

        private Stage build(Timing timing, String stageId, long duration, String parentId) {
            List<Entry> entries = timing.stream()
                    .filter(e -> Objects.equals(parentId, e.getParentId())).collect(toList());
            return new Stage(stageId, getChildren(timing, entries), duration);
        }

        private List<Stage> getChildren(Timing timing, List<Entry> entries) {
            List<Stage> children = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size() - 1; i++) {
                for (int j = i + 1; j < entries.size(); j++) {
                    Entry entry1 = entries.get(i);
                    Entry entry2 = entries.get(j);
                    String stageId = toStageId(entry1, entry2);
                    if (stageId != null) {
                        long duration = entry2.getTimestamp() - entry1.getTimestamp();
                        children.add(build(timing, stageId, duration, entry1.getId()));
                    }
                }
            }
            return children;
        }

        private String toStageId(Entry entry1, Entry entry2) {
            return mapping.get(new SimpleImmutableEntry<>(entry1.getId(), entry2.getId()));
        }
    }

    public final static class Stage {
        private final String id;
        private final List<Stage> children;
        private final long duration;

        public Stage(String id, List<Stage> children, long duration) {
            this.id = id;
            this.children = children;
            this.duration = duration;
        }

        public String getId() {
            return id;
        }

        public List<Stage> getChildren() {
            return children;
        }

        public long getDuration() {
            return duration;
        }

        private String toString(int trailing) {
            String whitespace = repeat(" ", trailing);
            StringBuilder sb = new StringBuilder()
                    .append(whitespace)
                    .append(id)
                    .append(": ")
                    .append(duration / 1000)
                    .append(" mcs\n");
            children.forEach(s -> sb.append(s.toString(trailing + 4)));
            if (!children.isEmpty()) {
                long subtotal = children.stream().collect(summarizingLong(Stage::getDuration)).getSum() / 1000;
                sb.append(whitespace)
                        .append("--- ")
                        .append("Subtotal: ")
                        .append(subtotal)
                        .append(" mcs\n");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return toString(0);
        }
    }
}
