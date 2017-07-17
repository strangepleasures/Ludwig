package foo.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AndNode extends Node {
    private final List<Node> nodes = new ArrayList<>();

    @Override
    public <T> T accept(NodeVisitor<T> visitor) {
        return visitor.visitAnd(this);
    }
}
