package org.batfish.specifier.parboiled;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.specifier.InterfaceSpecifier;
import org.batfish.specifier.InterfaceWithConnectedIpsSpecifier;
import org.batfish.specifier.NameInterfaceSpecifier;
import org.batfish.specifier.NameRegexInterfaceSpecifier;
import org.batfish.specifier.NodeSpecifierInterfaceSpecifier;
import org.batfish.specifier.ReferenceInterfaceGroupInterfaceSpecifier;
import org.batfish.specifier.SpecifierContext;
import org.batfish.specifier.TypesInterfaceSpecifier;
import org.batfish.specifier.VrfNameInterfaceSpecifier;
import org.batfish.specifier.ZoneNameInterfaceSpecifier;
import org.batfish.specifier.parboiled.ParboiledIpSpaceSpecifier.IpSpaceAstNodeToIpSpace;

/** An {@link InterfaceSpecifier} that resolves based on the AST generated by {@link Parser}. */
@ParametersAreNonnullByDefault
final class ParboiledInterfaceSpecifier implements InterfaceSpecifier {

  @ParametersAreNonnullByDefault
  private final class InterfaceAstNodeToInterfaces
      implements InterfaceAstNodeVisitor<Set<NodeInterfacePair>> {
    private final SpecifierContext _ctxt;
    private final Set<String> _nodes;

    InterfaceAstNodeToInterfaces(Set<String> nodes, SpecifierContext ctxt) {
      _nodes = nodes;
      _ctxt = ctxt;
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitConnectedToInterfaceAstNode(
        ConnectedToInterfaceAstNode connectedToInterfaceAstNode) {
      return new InterfaceWithConnectedIpsSpecifier(
              connectedToInterfaceAstNode
                  .getIpSpaceAstNode()
                  .accept(new IpSpaceAstNodeToIpSpace(_ctxt)))
          .resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitDifferenceInterfaceAstNode(
        DifferenceInterfaceAstNode differenceInterfaceAstNode) {
      return Sets.difference(
          differenceInterfaceAstNode.getLeft().accept(this),
          differenceInterfaceAstNode.getRight().accept(this));
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitInterfaceGroupInterfaceAstNode(
        InterfaceGroupInterfaceAstNode interfaceGroupInterfaceAstNode) {
      return new ReferenceInterfaceGroupInterfaceSpecifier(
              interfaceGroupInterfaceAstNode.getInterfaceGroup(),
              interfaceGroupInterfaceAstNode.getReferenceBook())
          .resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitIntersectionInterfaceAstNode(
        IntersectionInterfaceAstNode intersectionInterfaceAstNode) {
      return Sets.intersection(
          intersectionInterfaceAstNode.getLeft().accept(this),
          intersectionInterfaceAstNode.getRight().accept(this));
    }

    @Override
    public Set<NodeInterfacePair> visitInterfaceWithNodeInterfaceAstNode(
        InterfaceWithNodeInterfaceAstNode interfaceWithNodeInterfaceAstNode) {
      return Sets.intersection(
          new NodeSpecifierInterfaceSpecifier(
                  new ParboiledNodeSpecifier(interfaceWithNodeInterfaceAstNode.getNodeAstNode()))
              .resolve(_nodes, _ctxt),
          new ParboiledInterfaceSpecifier(interfaceWithNodeInterfaceAstNode.getInterfaceAstNode())
              .resolve(_nodes, _ctxt));
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitNameInterfaceNode(
        NameInterfaceAstNode nameInterfaceAstNode) {
      return new NameInterfaceSpecifier(nameInterfaceAstNode.getName()).resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitNameRegexInterfaceAstNode(
        NameRegexInterfaceAstNode nameRegexInterfaceAstNode) {
      return new NameRegexInterfaceSpecifier(nameRegexInterfaceAstNode.getPattern())
          .resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitTypeInterfaceNode(
        TypeInterfaceAstNode typeInterfaceAstNode) {
      return new TypesInterfaceSpecifier(ImmutableSet.of(typeInterfaceAstNode.getInterfaceType()))
          .resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitUnionInterfaceAstNode(
        UnionInterfaceAstNode unionInterfaceAstNode) {
      return Sets.union(
          unionInterfaceAstNode.getLeft().accept(this),
          unionInterfaceAstNode.getRight().accept(this));
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitVrfInterfaceAstNode(
        VrfInterfaceAstNode vrfInterfaceAstNode) {
      return new VrfNameInterfaceSpecifier(vrfInterfaceAstNode.getVrfName()).resolve(_nodes, _ctxt);
    }

    @Nonnull
    @Override
    public Set<NodeInterfacePair> visitZoneInterfaceAstNode(
        ZoneInterfaceAstNode zoneInterfaceAstNode) {
      return new ZoneNameInterfaceSpecifier(zoneInterfaceAstNode.getZoneName())
          .resolve(_nodes, _ctxt);
    }
  }

  private final InterfaceAstNode _ast;

  ParboiledInterfaceSpecifier(InterfaceAstNode ast) {
    _ast = ast;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ParboiledInterfaceSpecifier)) {
      return false;
    }
    return Objects.equals(_ast, ((ParboiledInterfaceSpecifier) o)._ast);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_ast);
  }

  @Override
  public Set<NodeInterfacePair> resolve(Set<String> nodes, SpecifierContext ctxt) {
    return _ast.accept(new InterfaceAstNodeToInterfaces(nodes, ctxt));
  }
}
