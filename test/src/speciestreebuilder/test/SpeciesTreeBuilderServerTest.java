package speciestreebuilder.test;

import genomeannotationapi.GenomeAnnotationAPIClient;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.forester.io.parsers.nhx.NHXParser;
import org.forester.phylogeny.Phylogeny;
import org.ini4j.Ini;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import speciestreebuilder.ConstructSpeciesTreeParams;
import speciestreebuilder.SpeciesTreeBuilderServer;
import tmpgnmanntest.PrepareTestGenomeAnnotationFromProteinsParams;
import tmpgnmanntest.TmpGnmAnnTestClient;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthService;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.FastaReader;
import us.kbase.kbasegenomes.Feature;
import us.kbase.kbasegenomes.Genome;
import us.kbase.kbasetrees.Tree;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

public class SpeciesTreeBuilderServerTest {
    private static AuthToken token = null;
    private static Map<String, String> config = null;
    private static WorkspaceClient wsClient = null;
    private static String wsName = null;
    private static String publicWsName = null;
    private static SpeciesTreeBuilderServer impl = null;
    
    @BeforeClass
    public static void init() throws Exception {
        token = AuthService.validateToken(System.getenv("KB_AUTH_TOKEN"));
        String configFilePath = System.getenv("KB_DEPLOYMENT_CONFIG");
        File deploy = new File(configFilePath);
        Ini ini = new Ini(deploy);
        config = ini.get("SpeciesTreeBuilder");
        wsClient = new WorkspaceClient(new URL(config.get("workspace-url")), token);
        wsClient.setIsInsecureHttpConnectionAllowed(true);
        // These lines are necessary because we don't want to start linux syslog bridge service
        JsonServerSyslog.setStaticUseSyslog(false);
        JsonServerSyslog.setStaticMlogFile(new File(config.get("scratch"), "test.log").getAbsolutePath());
        impl = new SpeciesTreeBuilderServer();
        impl.getConfig().put("public.genomes.ws", getPublicWsName());
        // Store test genome (real data)
        FastaReader fr = new FastaReader(new File("test/data", "Shewanella_ANA_3_uid58347.fasta"));
        Map<String, String> proteinIdToSeq = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fr.readAll().entrySet())
            proteinIdToSeq.put(entry.getKey(), entry.getValue());
        String genomeName = "Shewanella_ANA_3_uid58347";
        String genomeObjId = genomeName + ".genome";
        TmpGnmAnnTestClient tgat = new TmpGnmAnnTestClient(new URL(System.getenv("SDK_CALLBACK_URL")), token);
        tgat.setIsInsecureHttpConnectionAllowed(true);
        tgat.prepareTestGenomeAnnotationFromProteins(
                new PrepareTestGenomeAnnotationFromProteinsParams().withGenomeName(genomeName)
                .withProteinIdToSequence(proteinIdToSeq)
                .withOutputWorkspaceName(getWsName()).withOutputObjectName(genomeObjId));
        // Sync genome objects (just fake wrappers with kbase-id as name)
        String genomeWsType = "KBaseGenomes.Genome";
        List<ObjectSaveData> objects = new ArrayList<ObjectSaveData>();
        List<String> genomeKbIds = Arrays.asList(
                "kb|g.371",    // Shewanella oneidensis MR-1
                "kb|g.372",    // Shewanella oneidensis MR-1
                "kb|g.852",    // Shewanella putrefaciens CN-32
                "kb|g.1032",   // Shewanella sp. W3-18-1 
                "kb|g.1283",   // Shewanella baltica OS195
                "kb|g.1305",   // Shewanella baltica OS185
                "kb|g.1346",   // Shewanella baltica OS223
                "kb|g.2626",   // Shewanella sp. MR-4
                "kb|g.2627",   // Shewanella sp. MR-7
                "kb|g.2990",   // Shewanella baltica OS117
                "kb|g.2992",   // Shewanella baltica OS678
                "kb|g.3779",   // Shewanella sp. ANA-3
                "kb|g.20848",  // Shewanella oneidensis MR-1
                "kb|g.25423",  // Shewanella sp. POL2
                "kb|g.26614",  // Shewanella putrefaciens 200
                "kb|g.26354",  // Shewanella baltica OS155
                "kb|g.27369",  // Shewanella baltica OS625
                "kb|g.27370",  // Shewanella baltica OS678
                "kb|g.210723", // Shewanella decolorationis S1201
                "kb|g.242813"  // Shewanella xiamenensis
                );
        for (String kbId : genomeKbIds) {
            Map<String, Object> data = new LinkedHashMap<String, Object>(4);
            data.put("id", kbId);
            data.put("scientific_name", "");
            data.put("domain", "");
            data.put("genetic_code", 11L);
            objects.add(new ObjectSaveData().withName(kbId).withType(genomeWsType).withData(new UObject(data)));
        }
        wsClient.saveObjects(new SaveObjectsParams().withWorkspace(getPublicWsName()).withObjects(objects));
    }
    
    private static String getWsName() throws Exception {
        if (wsName == null) {
            long suffix = System.currentTimeMillis();
            wsName = "test_SpeciesTreeBuilder_" + suffix;
            wsClient.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
        }
        return wsName;
    }
    
    private static String getPublicWsName() throws Exception {
        if (publicWsName == null) {
            long suffix = System.currentTimeMillis();
            publicWsName = "test_SpeciesTreeBuilder_" + suffix;
            wsClient.createWorkspace(new CreateWorkspaceParams().withWorkspace(publicWsName));
        }
        return publicWsName;
    }
    
    private static RpcContext getContext() {
        return new RpcContext().withProvenance(Arrays.asList(new ProvenanceAction()
            .withService("SpeciesTreeBuilder").withMethod("please_never_use_it_in_production")
            .withMethodParams(new ArrayList<UObject>())));
    }
    
    @AfterClass
    public static void cleanup() {
        if (wsName != null) {
            try {
                wsClient.deleteWorkspace(new WorkspaceIdentity().withWorkspace(wsName));
                System.out.println("Test workspace was deleted");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (publicWsName != null) {
            try {
                wsClient.deleteWorkspace(new WorkspaceIdentity().withWorkspace(publicWsName));
                System.out.println("Test public workspace was deleted");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    protected static void saveWsObject(String wsName, String type, String objName, Object data) throws Exception {
        wsClient.saveObjects(new SaveObjectsParams().withWorkspace(wsName)
                .withObjects(Arrays.asList(new ObjectSaveData()
                .withType(type).withName(objName).withData(new UObject(data)))));
    }
    
    protected static <T> T getWsObject(String ref, Class<T> type) throws Exception {
        T ret = wsClient.getObjects2(new GetObjects2Params().withObjects(Arrays.asList(
                new ObjectSpecification().withRef(ref)))).getData().get(0).getData().asClassInstance(type);
        return ret;
    }

    private static List<String> extractLeafNodeNames(String tree) throws Exception {
        NHXParser parser = new NHXParser();
        parser.setSource(tree);
        List<String> leafNodeNames = new ArrayList<String>();
        for (Phylogeny ph : parser.parse())
            leafNodeNames.addAll(Arrays.asList(ph.getAllExternalNodeNames()));
        return leafNodeNames;
    }

    @Test
    public void testConstructSpeciesTree() throws Exception {
        String genomeName = "Shewanella_ANA_3_uid58347";
        String genomeId = genomeName + ".genome";
        String spTreeId = "sptree.1";
        String genomeRef = getWsName() + "/" + genomeId;
        String treeRef = impl.constructSpeciesTree(new ConstructSpeciesTreeParams().withNewGenomes(
                Arrays.asList(genomeRef)).withOutWorkspace(getWsName())
                .withOutTreeId(spTreeId).withUseRibosomalS9Only(0L).withNearestGenomeCount(20L), 
                token, getContext());
        Tree tree = getWsObject(treeRef, Tree.class);
        try {
            List<String> nodeIds = extractLeafNodeNames(tree.getTree());
            Assert.assertEquals(21, nodeIds.size());
            for (String nodeId : nodeIds) {
                String label = tree.getDefaultNodeLabels().get(nodeId);
                Assert.assertNotNull(label);
                Map<String, List<String>> refs = tree.getWsRefs().get(nodeId);
                Assert.assertEquals(1, refs.size());
            }
        } catch (Exception ex) {
            System.err.println(tree.getTree());
            throw ex;
        }
    }
}
