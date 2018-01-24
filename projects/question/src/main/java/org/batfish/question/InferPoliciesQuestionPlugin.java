package org.batfish.question;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.Function;
import org.batfish.common.Answerer;
import org.batfish.common.BatfishException;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.NodeRoleSpecifier;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.collections.NamedStructureOutlierSet;
import org.batfish.datamodel.collections.OutlierSet;
import org.batfish.datamodel.collections.RoleBasedOutlierSet;
import org.batfish.datamodel.questions.IRoleConsistencyQuestion;
import org.batfish.datamodel.questions.Question;
import org.batfish.question.NamedStructureRoleConsistencyQuestionPlugin.NamedStructureRoleConsistencyQuestion;
import org.batfish.question.OutliersQuestionPlugin.OutliersAnswerElement;
import org.batfish.question.OutliersQuestionPlugin.OutliersQuestion;
import org.batfish.question.PerRoleQuestionPlugin.PerRoleAnswerElement;
import org.batfish.question.PerRoleQuestionPlugin.PerRoleQuestion;
import org.batfish.question.RoleConsistencyQuestionPlugin.RoleConsistencyQuestion;
import org.batfish.role.OutliersHypothesis;

@AutoService(Plugin.class)
public class InferPoliciesQuestionPlugin extends QuestionPlugin {

  public static class InferPoliciesAnswerElement implements AnswerElement {

    private static final String PROP_ROLE_CONSISTENCY_QUESTIONS = "roleConsistencyQuestions";

    private List<IRoleConsistencyQuestion> _roleConsistencyQuestions;

    public InferPoliciesAnswerElement() {
      _roleConsistencyQuestions = new LinkedList<>();
    }

    @Override
    public String prettyPrint() {
      StringBuilder sb = new StringBuilder("Results for infer policies\n");

      for (IRoleConsistencyQuestion question : _roleConsistencyQuestions) {

        sb.append("Policy: nodes in the same role should have ");
        switch (question.getHypothesis()) {
          case SAME_DEFINITION:
            NamedStructureRoleConsistencyQuestion nsrcq =
                (NamedStructureRoleConsistencyQuestion) question;
            sb.append(
                "the same definition for same-named structures of type " + nsrcq.getStructType());
            break;
          case SAME_NAME:
            NamedStructureRoleConsistencyQuestion nsrcq2 =
                (NamedStructureRoleConsistencyQuestion) question;
            sb.append("same-named structures of type " + nsrcq2.getStructType());
            break;
          case SAME_SERVERS:
            RoleConsistencyQuestion rcq = (RoleConsistencyQuestion) question;
            sb.append("the same " + rcq.getPropertyName());
            break;
          default:
            throw new BatfishException("Unrecognized hypothesis " + question.getHypothesis());
        }
        sb.append("\n");
        Optional<NodeRoleSpecifier> specifierOpt = question.getRoleSpecifier();
        if (specifierOpt.isPresent()) {
          NodeRoleSpecifier specifier = specifierOpt.get();
          List<String> roleRegexes = specifier.getRoleRegexes();
          SortedMap<String, SortedSet<String>> roleMap = specifier.getRoleMap();

          sb.append("Role specifier:\n");
          if (!roleRegexes.isEmpty()) {
            sb.append("  Role regexes: \n");
            for (String regex : roleRegexes) {
              sb.append("    " + regex + "\n");
            }
          }
          if (!roleMap.isEmpty()) {
            sb.append("  Role map: \n");
            for (Map.Entry<String, SortedSet<String>> entry : specifier.getRoleMap().entrySet()) {
              sb.append("    " + entry + "\n");
            }
          }
        }
      }

      return sb.toString();
    }

    @JsonProperty(PROP_ROLE_CONSISTENCY_QUESTIONS)
    public List<IRoleConsistencyQuestion> getRoleConsistencyQuestions() {
      return _roleConsistencyQuestions;
    }

    public void addRoleConsistencyQuestions(
        List<IRoleConsistencyQuestion> roleConsistencyQuestions) {
      _roleConsistencyQuestions.addAll(roleConsistencyQuestions);
    }
  }

  public static class InferPoliciesAnswerer extends Answerer {

    private InferPoliciesAnswerElement _answerElement;

    // the minimum percentage of nodes that must conform to a policy, in order for
    // the policy to be returned
    private static final double CONFORMERS_THRESHOLD = 0.9;

    public InferPoliciesAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public InferPoliciesAnswerElement answer() {

      _answerElement = new InferPoliciesAnswerElement();

      _answerElement.addRoleConsistencyQuestions(serverConsistencyPolicies());
      _answerElement.addRoleConsistencyQuestions(
          namedStructureConsistencyPolicies(OutliersHypothesis.SAME_NAME));
      _answerElement.addRoleConsistencyQuestions(
          namedStructureConsistencyPolicies(OutliersHypothesis.SAME_DEFINITION));

      return _answerElement;
    }

    private List<IRoleConsistencyQuestion> serverConsistencyPolicies() {

      OutliersHypothesis hypothesis = OutliersHypothesis.SAME_SERVERS;

      SortedMap<String, AnswerElement> roleAnswers = perRoleOutlierInfo(hypothesis);

      Multimap<String, OutlierSet<NavigableSet<String>>> outliersPerPropertyName =
          outliersByProperty(
              roleAnswers.values(), OutliersAnswerElement::getServerOutliers, OutlierSet::getName);

      // remove outlier sets where no nodes declare any servers
      List<String> undeclared = new LinkedList<>();
      for (String name : outliersPerPropertyName.keySet()) {
        Collection<OutlierSet<NavigableSet<String>>> outlierSets =
            outliersPerPropertyName.get(name);
        if (outlierSets
            .stream()
            .allMatch(oset -> oset.getDefinition().isEmpty() && oset.getOutliers().isEmpty())) {
          undeclared.add(name);
        }
      }
      for (String name : undeclared) {
        outliersPerPropertyName.removeAll(name);
      }

      return policiesAboveThreshold(outliersPerPropertyName, hypothesis);
    }

    private List<IRoleConsistencyQuestion> namedStructureConsistencyPolicies(
        OutliersHypothesis hypothesis) {

      SortedMap<String, AnswerElement> roleAnswers = perRoleOutlierInfo(hypothesis);

      Multimap<String, NamedStructureOutlierSet<?>> outliersPerStructureType =
          outliersByProperty(
              roleAnswers.values(),
              OutliersAnswerElement::getNamedStructureOutliers,
              NamedStructureOutlierSet::getStructType);

      return policiesAboveThreshold(outliersPerStructureType, hypothesis);
    }

    // obtain all outlier sets for the given hypothesis
    private SortedMap<String, AnswerElement> perRoleOutlierInfo(OutliersHypothesis hypothesis) {
      OutliersQuestion innerQ = new OutliersQuestionPlugin().createQuestion();
      innerQ.setHypothesis(hypothesis);
      innerQ.setVerbose(true);

      PerRoleQuestionPlugin outerPlugin = new PerRoleQuestionPlugin();
      PerRoleQuestion outerQ = outerPlugin.createQuestion();
      outerQ.setQuestion(innerQ);

      PerRoleAnswerElement roleAE = outerPlugin.createAnswerer(outerQ, _batfish).answer();

      SortedMap<String, AnswerElement> answers = roleAE.getAnswers();
      for (Map.Entry<String, AnswerElement> entry : answers.entrySet()) {
        String role = entry.getKey();
        OutliersAnswerElement oae = (OutliersAnswerElement) entry.getValue();
        // update each outlier set to know its associated role
        setRoleAll(oae.getServerOutliers(), role);
        setRoleAll(oae.getNamedStructureOutliers(), role);
      }
      return answers;
    }

    private <T extends RoleBasedOutlierSet> Multimap<String, T> outliersByProperty(
        Collection<AnswerElement> answers,
        Function<OutliersAnswerElement, Collection<T>> outliersFun,
        Function<T, String> propertyNameFun) {

      Multimap<String, T> outliersPerProperty = LinkedListMultimap.create();

      // partition the resulting outliers by structure type (e.g., Ip Access List, Route Map)
      for (AnswerElement ae : answers) {
        OutliersAnswerElement oae = (OutliersAnswerElement) ae;
        for (T os : outliersFun.apply(oae)) {
          outliersPerProperty.put(propertyNameFun.apply(os), os);
        }
      }
      return outliersPerProperty;
    }

    private <T extends RoleBasedOutlierSet> List<IRoleConsistencyQuestion> policiesAboveThreshold(
        Multimap<String, T> outliersPerPropertyName, OutliersHypothesis hypothesis) {
      NodeRoleSpecifier nodeRoleSpecifier = _batfish.getNodeRoleSpecifier(false);
      List<IRoleConsistencyQuestion> policies = new LinkedList<>();
      for (String name : outliersPerPropertyName.keySet()) {
        Collection<T> outlierSets = outliersPerPropertyName.get(name);
        int conformers = outlierSets.stream().mapToInt(oset -> oset.getConformers().size()).sum();
        int outliers = outlierSets.stream().mapToInt(oset -> oset.getOutliers().size()).sum();
        double all = (double) conformers + outliers;
        if (conformers / all >= CONFORMERS_THRESHOLD) {
          switch (hypothesis) {
            case SAME_DEFINITION:
            case SAME_NAME:
              NamedStructureRoleConsistencyQuestion policy =
                  new NamedStructureRoleConsistencyQuestion();
              policy.setHypothesis(hypothesis);
              policy.setRoleSpecifier(nodeRoleSpecifier);
              policy.setStructType(name);
              policies.add(policy);
              break;
            case SAME_SERVERS:
              RoleConsistencyQuestion rcpolicy = new RoleConsistencyQuestion();
              rcpolicy.setRoleSpecifier(nodeRoleSpecifier);
              rcpolicy.setPropertyName(name);
              policies.add(rcpolicy);
              break;
            default:
              throw new BatfishException("Unrecognized hypothesis " + hypothesis);
          }
        }
      }
      return policies;
    }

    private void setRoleAll(Collection<? extends RoleBasedOutlierSet> outlierSets, String role) {
      for (RoleBasedOutlierSet os : outlierSets) {
        os.setRole(role);
      }
    }
  }

  // <question_page_comment>
  /**
   * Infer likely role-based consistency policies for the network. Each such policy in general has
   * the form: all nodes that have the same role, according to a given role specifier S, must be
   * consistent with one another in some particular way.
   *
   * @type InferPolicies multifile
   */
  public static final class InferPoliciesQuestion extends Question {

    public InferPoliciesQuestion() {}

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "InferPolicies";
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new InferPoliciesAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new InferPoliciesQuestion();
  }
}
