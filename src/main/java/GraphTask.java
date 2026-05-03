import java.util.*;

/**
 * Container class that formally groups all graph-related classes into one unit.
 * Implements a Spanning Tree Protocol (STP) simulation on a weighted undirected graph.
 * Each undirected edge is represented internally as two directed arcs.
 *
 * <p>STP elects a root bridge by priority, computes cheapest paths from every
 * switch to the root via simulated BPDU exchange, and assigns port roles
 * (ROOT, DESIGNATED, BLOCKED) to eliminate loops while keeping the network connected.</p>
 */
public class GraphTask {

   /**
    * Entry point. Creates a GraphTask instance and delegates to run().
    *
    * @param args command-line arguments, not used
    */
   public static void main(String[] args) {
      GraphTask a = new GraphTask();
      a.run();
   }

   /**
    * Actual main method. Builds example graphs and runs STP simulation on them.
    * Add further experiments here.
    */
   public void run() {
      Graph g = new Graph("G");

      Vertex sw1 = g.createVertex("SW1"); sw1.priority = 4096;
      Vertex sw2 = g.createVertex("SW2"); sw2.priority = 8192;
      Vertex sw3 = g.createVertex("SW3"); sw3.priority = 8192;
      Vertex sw4 = g.createVertex("SW4"); sw4.priority = 16384;

      g.createLink("l1", sw1, sw2, 19);
      g.createLink("l2", sw1, sw3, 100);
      g.createLink("l3", sw2, sw3, 4);
      g.createLink("l4", sw2, sw4, 19);
      g.createLink("l5", sw3, sw4, 4);

      System.out.println(g);
      List<Arc> tree = g.runSTP();
      System.out.println("\nSpanning tree arcs: " + tree);
   }

   /**
    * Represents a network switch (bridge) in the STP topology.
    *
    * <p>Structurally, vertices form a singly-linked list via {@code next}.
    * Each vertex owns a singly-linked list of outgoing arcs via {@code first}.
    * STP state is stored directly on the vertex and updated during simulation.</p>
    */
   class Vertex {

      /** Unique human-readable identifier for this switch, e.g. "SW1". */
      private String id;

      /**
       * Next vertex in the graph's linked list of all vertices.
       * Null if this is the last vertex.
       */
      private Vertex next;

      /**
       * First arc in this vertex's linked list of outgoing arcs.
       * Null if this vertex has no outgoing arcs.
       */
      private Arc first;

      /**
       * General-purpose integer field available for algorithms.
       * Warning: corrupted as a side effect of {@link Graph#createAdjMatrix()}.
       */
      private int info = 0;

      /**
       * Administrator-configured bridge priority used for root election.
       * Lower value means this switch is preferred as root bridge.
       * IEEE 802.1D default is 32768. Valid values are multiples of 4096
       * in the range 0–65535. Never computed — always set by the administrator.
       */
      int priority = 32768;

      /**
       * Accumulated STP path cost from this switch to the root bridge.
       * Computed during BPDU exchange by summing link costs along the best path.
       * Initialized to {@link Integer#MAX_VALUE} (infinity) before simulation.
       * Zero on the root bridge itself.
       */
      int rootCost = Integer.MAX_VALUE;

      /**
       * The directly connected neighbor that advertised the best path to root.
       * Represents the next hop when forwarding traffic toward the root bridge.
       * Null on the root bridge (it has no upstream).
       * Set during BPDU exchange via {@link #receiveBPDU(int, int, Vertex)}.
       */
      Vertex stpParent = null;

      /**
       * Administrator-configured bridge priority used to initialize the simulation.
       * Tracks the best root priority heard so far during BPDU exchange.
       * Starts equal to this switch's own priority (every switch nominates itself).
       * Converges to the true root's priority once simulation completes.
       */
      int bestRootPriority = priority;

      /**
       * Flag indicating whether this switch updated its STP state during the current round.
       * Set to true inside {@link #receiveBPDU(int, int, Vertex)} when state changes.
       * Reset to false at the start of each round by the main simulation loop.
       * When no switch has changed after a full round, the network has converged.
       */
      boolean changed = false;

      /**
       * Full constructor used internally by the graph.
       *
       * @param s identifier for this vertex
       * @param v next vertex in the linked list
       * @param e first arc in this vertex's arc list
       */
      Vertex(String s, Vertex v, Arc e) {
         id = s;
         next = v;
         first = e;
      }

      /**
       * Convenience constructor. Creates a vertex with no next vertex and no arcs.
       *
       * @param s identifier for this vertex
       */
      Vertex(String s) {
         this(s, null, null);
      }

      /**
       * Initializes this switch for a fresh STP simulation run.
       * Every switch starts by assuming it is the root bridge — exactly
       * as a real switch behaves on boot before hearing any BPDUs.
       * Resets all STP fields so runSTP() can safely be called multiple times.
       */
      void initBPDU() {
         bestRootPriority = priority;
         rootCost = 0;
         stpParent = null;
         changed = true;
      }

      /**
       * Sends this switch's current BPDU to all directly connected neighbors.
       * BPDU content is passed as plain method arguments rather than objects.
       * Corresponds to a real switch broadcasting BPDUs out all active ports.
       * Each neighbor receives: advertised root priority, path cost via this switch,
       * and a reference to this switch as the potential next hop.
       */
      void sendBPDUs() {
         Arc a = first;
         while (a != null) {
            a.target.receiveBPDU(bestRootPriority, rootCost + a.cost, this);
            a = a.next;
         }
      }

      /**
       * Processes an incoming BPDU from a directly connected neighbor.
       * Accepts the BPDU if it advertises a better root (lower priority number)
       * or the same root reachable at lower cost than currently known.
       * This is the core of distributed Bellman-Ford — the correct root and
       * cheapest paths emerge from repeated application of this rule alone.
       *
       * @param rootPri  priority of the root bridge as advertised by the sender
       * @param pathCost total accumulated cost to reach that root via the sender
       * @param sender   the neighboring switch that sent this BPDU,
       *                 becomes stpParent if this BPDU is accepted
       */
      void receiveBPDU(int rootPri, int pathCost, Vertex sender) {
         if (rootPri < bestRootPriority ||
            (rootPri == bestRootPriority && pathCost < rootCost)) {
            bestRootPriority = rootPri;
            rootCost = pathCost;
            stpParent = sender;
            changed = true;
         }
      }

      /**
       * Returns the identifier of this switch.
       *
       * @return vertex id string
       */
      @Override
      public String toString() {
         return id;
      }
   }

   /**
    * Represents a directed link between two switches in the graph.
    * Undirected edges are modelled as two opposite Arc objects.
    * Arcs form a singly-linked list per vertex via {@code next}.
    *
    * <p>After STP simulation, each arc carries a port role indicating
    * its forwarding state in the loop-free topology.</p>
    */
   class Arc {

      /** Unique human-readable identifier for this arc, e.g. "l1_SW1_SW2". */
      private String id;

      /**
       * The switch at the destination end of this directed arc.
       * Never null in a well-formed graph.
       */
      private Vertex target;

      /**
       * Next arc in the source vertex's linked list of outgoing arcs.
       * Null if this is the last arc from that vertex.
       */
      private Arc next;

      /**
       * General-purpose integer field available for algorithms.
       * Warning: corrupted as a side effect of {@link Graph#createAdjMatrix()}.
       */
      private int info = 0;

      /**
       * STP path cost for this link, representing the penalty for using it.
       * Lower cost means faster/preferred link. IEEE 802.1D standard values:
       * 100 for 10Mbps, 19 for 100Mbps, 4 for 1Gbps, 2 for 10Gbps.
       * Set at link creation time via {@link Graph#createLink(String, Vertex, Vertex, int)}.
       */
      int cost = 19;

      /**
       * STP port role assigned to this arc after simulation convergence.
       * Possible values:
       * <ul>
       *   <li>ROOT — this port's best path toward the root bridge.
       *       One per non-root switch. Receives and forwards traffic.</li>
       *   <li>DESIGNATED — the root-side port on each active link.
       *       Forwards traffic downstream toward non-root switches.</li>
       *   <li>BLOCKED — redundant port that would cause a loop if active.
       *       Receives BPDUs only; drops all other traffic.</li>
       *   <li>UNSET — initial value before role assignment.</li>
       * </ul>
       */
      String role = "UNSET";

      /**
       * Full constructor used internally by the graph.
       *
       * @param s identifier for this arc
       * @param v target vertex this arc points to
       * @param a next arc in the source vertex's arc list
       */
      Arc(String s, Vertex v, Arc a) {
         id = s;
         target = v;
         next = a;
      }

      /**
       * Convenience constructor. Creates an arc with no target and no next arc.
       *
       * @param s identifier for this arc
       */
      Arc(String s) {
         this(s, null, null);
      }

      /**
       * Returns the identifier and current role of this arc.
       *
       * @return string in the form "id[role]"
       */
      @Override
      public String toString() {
         return id + "[" + role + "]";
      }
   }

   /**
    * Represents the network topology as a linked list of vertices,
    * each owning a linked list of outgoing arcs.
    *
    * <p>Provides graph construction methods, STP simulation, and
    * the original random graph generators from the template.</p>
    */
   class Graph {

      /** Human-readable identifier for this graph. */
      private String id;

      /**
       * First vertex in the graph's linked list of all vertices.
       * Null if the graph has no vertices.
       */
      private Vertex first;

      /**
       * General-purpose integer field, used by {@link #createAdjMatrix()}
       * to count vertices. May be overwritten by multiple algorithm calls.
       */
      private int info = 0;

      /**
       * Full constructor.
       *
       * @param s identifier for this graph
       * @param v first vertex in the vertex linked list
       */
      Graph(String s, Vertex v) {
         id = s;
         first = v;
      }

      /**
       * Convenience constructor. Creates an empty graph with no vertices.
       *
       * @param s identifier for this graph
       */
      Graph(String s) {
         this(s, null);
      }

      /**
       * Returns a multi-line string representation of the graph.
       * Each line shows one vertex and all its outgoing arcs with targets and costs.
       *
       * @return formatted string of the full adjacency list
       */
      @Override
      public String toString() {
         String nl = System.getProperty("line.separator");
         StringBuffer sb = new StringBuffer(nl);
         sb.append(id); sb.append(nl);
         Vertex v = first;
         while (v != null) {
            sb.append(v.toString()); sb.append(" -->");
            Arc a = v.first;
            while (a != null) {
               sb.append(" ").append(a.id)
                 .append("(cost=").append(a.cost).append(")")
                 .append("->").append(a.target);
               a = a.next;
            }
            sb.append(nl); v = v.next;
         }
         return sb.toString();
      }

      /**
       * Creates a new vertex and prepends it to the graph's vertex list.
       *
       * @param vid identifier for the new vertex
       * @return the newly created vertex
       */
      public Vertex createVertex(String vid) {
         Vertex res = new Vertex(vid);
         res.next = first;
         first = res;
         return res;
      }

      /**
       * Creates a directed arc from one vertex to another and prepends it
       * to the source vertex's arc list.
       *
       * @param aid  identifier for the new arc
       * @param from source vertex
       * @param to   target vertex
       * @return the newly created arc
       */
      public Arc createArc(String aid, Vertex from, Vertex to) {
         Arc res = new Arc(aid);
         res.next = from.first;
         from.first = res;
         res.target = to;
         return res;
      }

      /**
       * Creates a bidirectional link between two vertices by creating two
       * opposite directed arcs, both with the given STP path cost.
       * This satisfies the requirement that undirected edges are represented
       * as two directed arcs.
       *
       * @param lid  identifier prefix for the two arcs created
       * @param a    first endpoint of the link
       * @param b    second endpoint of the link
       * @param cost STP path cost for this link in both directions
       */
      public void createLink(String lid, Vertex a, Vertex b, int cost) {
         Arc ab = createArc(lid + "_" + a + "_" + b, a, b); ab.cost = cost;
         Arc ba = createArc(lid + "_" + b + "_" + a, b, a); ba.cost = cost;
      }

      /**
       * Runs the full STP simulation on this graph using distributed BPDU exchange.
       * Mutates the graph in place as a side effect — every arc's role field and
       * every vertex's rootCost, stpParent, and bestRootPriority are updated.
       *
       * <p>Algorithm steps:
       * <ol>
       *   <li>Each switch nominates itself as root via {@link Vertex#initBPDU()}.</li>
       *   <li>Rounds of BPDU exchange run until no switch changes state (convergence).</li>
       *   <li>Port roles are assigned via {@link #assignPortRoles()}.</li>
       *   <li>ROOT arcs are collected and returned as the spanning tree.</li>
       * </ol>
       * </p>
       *
       * <p>Convergence is guaranteed to occur in at most d rounds, where d is the
       * diameter of the graph (length of the longest shortest path).</p>
       *
       * @return list of ROOT port arcs — exactly one per non-root switch, n-1 total —
       *         representing the loop-free spanning tree for further programmatic use.
       *         The mutated graph object remains fully queryable after this call.
       */
      public List<Arc> runSTP() {
         // 1. Every switch nominates itself as root
         Vertex v = first;
         while (v != null) { v.initBPDU(); v = v.next; }

         // 2. Main simulation loop — one iteration = one round of BPDU exchange
         int round = 0;
         boolean converged = false;
         while (!converged) {
            round++;
            v = first;
            while (v != null) { v.changed = false; v = v.next; }
            v = first;
            while (v != null) { v.sendBPDUs(); v = v.next; }
            converged = true;
            v = first;
            while (v != null) {
               if (v.changed) { converged = false; break; }
               v = v.next;
            }
         }
         System.out.println("Converged in " + round + " rounds.");

         // 3. Assign port roles — mutates arc.role on every arc in the graph
         assignPortRoles();

         // 4. Collect and return spanning tree as list of ROOT arcs
         List<Arc> spanningTree = new ArrayList<>();
         v = first;
         while (v != null) {
            Arc a = v.first;
            while (a != null) {
               if (a.role.equals("ROOT")) spanningTree.add(a);
               a = a.next;
            }
            v = v.next;
         }
         return spanningTree;
      }

      /**
       * Assigns STP port roles to every arc in the graph after convergence.
       * Mutates the role field on every arc. Called internally by {@link #runSTP()}.
       *
       * <p>Assignment rules in order:
       * <ol>
       *   <li>All arcs start as BLOCKED.</li>
       *   <li>ROOT: one arc per non-root switch, pointing toward stpParent.</li>
       *   <li>DESIGNATED: the parent's arc pointing down toward each child.</li>
       *   <li>Remaining BLOCKED pairs on redundant links: the side with lower
       *       rootCost becomes DESIGNATED, the other stays BLOCKED.</li>
       * </ol>
       * </p>
       */
      private void assignPortRoles() {
         // All ports start BLOCKED
         Vertex v = first;
         while (v != null) {
            Arc a = v.first;
            while (a != null) { a.role = "BLOCKED"; a = a.next; }
            v = v.next;
         }

         // Find root — the switch with rootCost 0
         Vertex root = first;
         v = first;
         while (v != null) {
            if (v.rootCost < root.rootCost) root = v;
            v = v.next;
         }

         // ROOT ports — one per non-root switch, arc pointing toward stpParent
         v = first;
         while (v != null) {
            if (v != root && v.stpParent != null) {
               Arc a = v.first;
               while (a != null) {
                  if (a.target == v.stpParent) { a.role = "ROOT"; break; }
                  a = a.next;
               }
            }
            v = v.next;
         }

         // DESIGNATED ports — parent's arc pointing down toward child
         v = first;
         while (v != null) {
            if (v != root && v.stpParent != null) {
               Arc a = v.stpParent.first;
               while (a != null) {
                  if (a.target == v) { a.role = "DESIGNATED"; break; }
                  a = a.next;
               }
            }
            v = v.next;
         }

         // Remaining BLOCKED pairs: closer side to root wins DESIGNATED
         v = first;
         while (v != null) {
            Arc a = v.first;
            while (a != null) {
               if (a.role.equals("BLOCKED")) {
                  Arc reverse = a.target.first;
                  while (reverse != null) {
                     if (reverse.target == v) break;
                     reverse = reverse.next;
                  }
                  if (reverse != null && reverse.role.equals("BLOCKED")) {
                     if (v.rootCost <= a.target.rootCost) a.role = "DESIGNATED";
                     else reverse.role = "DESIGNATED";
                  }
               }
               a = a.next;
            }
            v = v.next;
         }
      }

      /**
       * Creates a connected undirected random tree with n vertices.
       * Each new vertex is connected to a random existing vertex.
       *
       * @param n number of vertices to add to this graph
       */
      public void createRandomTree(int n) {
         if (n <= 0) return;
         Vertex[] varray = new Vertex[n];
         for (int i = 0; i < n; i++) {
            varray[i] = createVertex("v" + (n - i));
            if (i > 0) {
               int vnr = (int)(Math.random() * i);
               createArc("a" + varray[vnr] + "_" + varray[i], varray[vnr], varray[i]);
               createArc("a" + varray[i] + "_" + varray[vnr], varray[i], varray[vnr]);
            }
         }
      }

      /**
       * Creates an adjacency matrix of this graph.
       * Side effect: corrupts the info fields on all vertices.
       *
       * @return adjacency matrix where entry [i][j] is the number of arcs from i to j
       */
      public int[][] createAdjMatrix() {
         info = 0;
         Vertex v = first;
         while (v != null) { v.info = info++; v = v.next; }
         int[][] res = new int[info][info];
         v = first;
         while (v != null) {
            Arc a = v.first;
            while (a != null) { res[v.info][a.target.info]++; a = a.next; }
            v = v.next;
         }
         return res;
      }

      /**
       * Creates a connected simple undirected random graph with n vertices and m edges.
       * Starts with a random spanning tree then adds remaining edges randomly,
       * avoiding loops and duplicate edges.
       *
       * @param n number of vertices
       * @param m number of edges, must satisfy n-1 &lt;= m &lt;= n*(n-1)/2
       * @throws IllegalArgumentException if n exceeds 2500 or m is out of valid range
       */
      public void createRandomSimpleGraph(int n, int m) {
         if (n <= 0) return;
         if (n > 2500) throw new IllegalArgumentException("Too many vertices: " + n);
         if (m < n-1 || m > n*(n-1)/2)
            throw new IllegalArgumentException("Impossible number of edges: " + m);
         first = null;
         createRandomTree(n);
         Vertex[] vert = new Vertex[n];
         Vertex v = first; int c = 0;
         while (v != null) { vert[c++] = v; v = v.next; }
         int[][] connected = createAdjMatrix();
         int edgeCount = m - n + 1;
         while (edgeCount > 0) {
            int i = (int)(Math.random()*n), j = (int)(Math.random()*n);
            if (i==j || connected[i][j]!=0 || connected[j][i]!=0) continue;
            createArc("a"+vert[i]+"_"+vert[j], vert[i], vert[j]); connected[i][j]=1;
            createArc("a"+vert[j]+"_"+vert[i], vert[j], vert[i]); connected[j][i]=1;
            edgeCount--;
         }
      }
   }
}
