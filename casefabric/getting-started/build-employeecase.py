"""
Compiles the Case Designer source files of the employee case
(src/definitions/employeecase.case/.type/.caseteam/.dimensions + the referenced .humantask and
.process files) into the single deployable CMMN file target/definitions/employeecase.xml.

Why this script exists: the Case Designer (http://localhost:2081) compiles a model in the
browser and only then POSTs the finished XML to its /repository/deploy endpoint, so there is no
headless way to deploy from the command line. Without it the deployed model silently drifts from
src/definitions (found 2026-10-21: the deployed employeecase.xml dated from 2026-10-19 and lacked
the "Release onboarding emails" process task). The output follows the same shape the Designer
produced for the 2026-10-19 deployment: plan items + sentries in each plan fragment, plan item
definitions next to them, case file item definitions, case roles, processes, diagram.

Usage (from casefabric/getting-started): python build-employeecase.py
The engine picks up the new file from target/definitions on the next case start
(volume ./target/definitions:/opt/cafienne/definitions).
"""
import copy
import os
import xml.etree.ElementTree as ET

CMMN = 'http://www.omg.org/spec/CMMN/20151109/MODEL'
CAF = 'org.cafienne'
ET.register_namespace('', CMMN)
ET.register_namespace('cafienne', CAF)
N = lambda tag: '{%s}%s' % (CMMN, tag)
C = lambda tag: '{%s}%s' % (CAF, tag)

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'src', 'definitions')
OUT = os.path.join(HERE, 'target', 'definitions', 'employeecase.xml')

PLAN_ITEM_TAGS = {N('humanTask'), N('milestone'), N('processTask'), N('stage')}
PROPERTY_TYPES = {'date': 'date', 'date-time': 'dateTime'}


def load(name):
    return ET.parse(os.path.join(SRC, name)).getroot()


def definition_id(plan_item_id):
    return plan_item_id[3:] if plan_item_id.startswith('pi_') else plan_item_id + '_def'


def sentry_from_criterion(criterion):
    sentry = ET.Element(N('sentry'), {'id': 's_' + criterion.get('id')})
    for child in criterion:
        sentry.append(copy.deepcopy(child))
    return sentry


def compile_human_task(src):
    task = ET.Element(N('humanTask'), {k: v for k, v in src.attrib.items() if k != 'id'})
    task.set('id', definition_id(src.get('id')))
    for child in src:
        if child.tag in (N('inputs'), N('outputs')):
            task.append(copy.deepcopy(child))
    ext_src = src.find(N('extensionElements'))
    impl_src = ext_src.find(C('implementation'))
    ref = impl_src.get('humanTaskRef')
    ht_impl = load(ref).find(C('implementation'))
    ext = ET.SubElement(task, N('extensionElements'), {'mustUnderstand': 'false'})
    impl = ET.SubElement(ext, C('implementation'), dict(ht_impl.attrib))
    impl.set('humanTaskRef', ref)
    for child in ht_impl:
        impl.append(copy.deepcopy(child))
    for child in impl_src:
        impl.append(copy.deepcopy(child))
    return task


def compile_process_task(src, processes):
    task = ET.Element(N('processTask'), {k: v for k, v in src.attrib.items() if k != 'id'})
    task.set('id', definition_id(src.get('id')))
    for child in src:
        if child.tag in (N('inputs'), N('outputs'), N('parameterMapping')):
            task.append(copy.deepcopy(child))
    processes.add(src.get('processRef'))
    return task


def compile_fragment(src_fragment, target_fragment, processes, definitions):
    """Fills target_fragment (casePlanModel or stage) with planItems and sentries; collects every
    plan item definition into `definitions`. CaseFabric 1.1.35 resolves definitionRef only among
    the casePlanModel's own children (confirmed live 2026-10-21: definitions nested inside a
    stage gave "getPlanItemDefinition() is null"), so all definitions go to the case plan."""
    plan_items, sentries = [], []
    for child in src_fragment:
        if child.tag not in PLAN_ITEM_TAGS:
            continue
        pid = child.get('id')
        item = ET.Element(N('planItem'), {'id': pid, 'name': child.get('name'),
                                          'definitionRef': definition_id(pid)})
        for part in child:
            if part.tag in (N('documentation'), N('itemControl')):
                item.append(copy.deepcopy(part))
        for part in child:
            if part.tag in (N('entryCriterion'), N('exitCriterion')):
                ET.SubElement(item, part.tag, {'id': part.get('id'), 'sentryRef': 's_' + part.get('id')})
                sentries.append(sentry_from_criterion(part))
        plan_items.append(item)

        if child.tag == N('humanTask'):
            definitions.append(compile_human_task(child))
        elif child.tag == N('processTask'):
            definitions.append(compile_process_task(child, processes))
        elif child.tag == N('milestone'):
            definitions.append(ET.Element(N('milestone'), {'id': definition_id(pid), 'name': child.get('name')}))
        elif child.tag == N('stage'):
            stage = ET.Element(N('stage'), {'id': definition_id(pid), 'name': child.get('name'),
                                           'autoComplete': child.get('autoComplete', 'false')})
            compile_fragment(child, stage, processes, definitions)
            definitions.append(stage)
    for el in plan_items + sentries:
        target_fragment.append(el)


def compile_type(type_root):
    defs = []
    for prop in type_root.find(N('schema')).findall(N('property')):
        cfid = ET.Element(N('caseFileItemDefinition'), {
            'name': prop.get('name'),
            'definitionType': 'http://www.omg.org/spec/CMMN/DefinitionType/Unspecified',
            'id': 'employeecase_type_%s.object' % prop.get('name')})
        for field in prop.find(N('schema')).findall(N('property')):
            ptype = PROPERTY_TYPES.get(field.get('format'), field.get('type'))
            ET.SubElement(cfid, N('property'), {
                'name': field.get('name'),
                'type': 'http://www.omg.org/spec/CMMN/PropertyType/' + ptype})
        defs.append((prop, cfid))
    return defs


def main():
    case_src = load('employeecase.case')
    type_defs = compile_type(load('employeecase.type'))
    team = load('employeecase.caseteam')

    root = ET.Element(N('definitions'))
    for _, cfid in type_defs:
        root.append(cfid)
    root.append(ET.Element(N('caseFileItemDefinition'), {
        'name': 'employeecase',
        'definitionType': 'http://www.omg.org/spec/CMMN/DefinitionType/Unspecified',
        'id': 'employeecase.type'}))

    case = ET.SubElement(root, N('case'), {'id': 'employeecase.case', 'name': 'employeecase',
                                           'expressionLanguage': case_src.get('expressionLanguage')})
    case.append(copy.deepcopy(case_src.find(N('documentation'))))
    cfm = ET.SubElement(case, N('caseFileModel'), {C('typeRef'): 'employeecase.type'})
    for prop, cfid in type_defs:
        ET.SubElement(cfm, N('caseFileItem'), {'id': prop.get('name'), 'name': prop.get('name'),
                                               'multiplicity': prop.get('multiplicity'),
                                               'definitionRef': cfid.get('id')})

    cpm_src = case_src.find(N('casePlanModel'))
    cpm = ET.SubElement(case, N('casePlanModel'), dict(cpm_src.attrib))
    doc = cpm_src.find(N('documentation'))
    if doc is not None:
        cpm.append(copy.deepcopy(doc))
    processes = set()
    definitions = []
    compile_fragment(cpm_src, cpm, processes, definitions)
    for el in definitions:
        cpm.append(el)

    roles = ET.SubElement(case, N('caseRoles'), {C('caseTeamRef'): 'employeecase.caseteam'})
    for role in team.findall(N('role')):
        ET.SubElement(roles, N('role'), dict(role.attrib))
    for inp in case_src.findall(N('input')):
        case.append(copy.deepcopy(inp))

    for ref in sorted(processes):
        proc = copy.deepcopy(load(ref))
        proc.set('id', ref)
        root.append(proc)

    dims = load('employeecase.dimensions')
    di = ET.SubElement(root, N('CMMNDI'))
    for diagram in dims.findall(N('CMMNDiagram')):
        di.append(copy.deepcopy(diagram))

    ET.indent(root, space='    ')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    ET.ElementTree(root).write(OUT, encoding='utf-8', xml_declaration=False)
    print('Wrote', OUT)


if __name__ == '__main__':
    main()
