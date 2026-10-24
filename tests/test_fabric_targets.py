"""Target contracts; optionally inspect built production jars with --jars."""
import json
from pathlib import Path
import sys
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JARS = '--jars' in sys.argv
if JARS:
    sys.argv.remove('--jars')

class FabricTargets(unittest.TestCase):
    def test_target_contracts(self):
        for target, api in [('26.2', '0.154.2'), ('26.3', '0.165.0')]:
            with self.subTest(target=target):
                module = 'version-' + target.replace('.', '-')
                self.assertIn(f'include("{module}")', (ROOT / 'settings.gradle').read_text())
                if JARS:
                    with zipfile.ZipFile(ROOT / module / 'build/libs' / f'admintools-{target}-1.0.0.jar') as jar:
                        data = json.loads(jar.read('fabric.mod.json'))
                        self.assertFalse(any('/test/' in n or 'ItemLogicTestRunner' in n for n in jar.namelist()))
                        config_name = 'admintools.mixins.json' if target == '26.2' else 'admintools-26-3.mixins.json'
                        mixins = json.loads(jar.read(config_name))
                        self.assertTrue(mixins['required'])
                        self.assertEqual(mixins['injectors']['defaultRequire'], 1)
                else:
                    data = json.loads((ROOT / module / 'src/main/resources/fabric.mod.json').read_text())
                    self.assertIn(f'archivesName = "admintools-{target}"', (ROOT / module / 'build.gradle').read_text())
                self.assertEqual(data['id'], 'admintools')
                self.assertEqual(data['version'], '1.0.0' if JARS else '${version}')
                self.assertEqual(data['environment'], 'server')
                self.assertEqual(data['entrypoints']['main'], ['com.echubbuck.admintools.AdminToolsMod'])
                self.assertNotIn('client', data['entrypoints'])
                self.assertEqual(data['depends'], {'minecraft': target, 'java': '>=25', 'fabricloader': '>=0.19.3', 'fabric-api': '>=' + api})
                self.assertEqual(data['mixins'], ['admintools.mixins.json'] if target == '26.2' else ['admintools-26-3.mixins.json'])
                if JARS:
                    self.assertNotIn('fabric-gametest', data['entrypoints'])


    def test_target_specific_player_drop_mixin(self):
        specifications = {
            '26.2': (
                'admintools.mixins.json', 'PlayerItemMixin',
                'version-26-2/src/main/java/com/echubbuck/admintools/mixin/PlayerItemMixin.java',
                'drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;'),
            '26.3': (
                'admintools-26-3.mixins.json', 'PlayerItemMixin26_3',
                'version-26-3/src/main/java/com/echubbuck/admintools/mixin/PlayerItemMixin26_3.java',
                'drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;'),
        }
        for target, (config_name, mixin_class, source_name, descriptor) in specifications.items():
            module = ROOT / ('version-' + target.replace('.', '-'))
            with self.subTest(target=target):
                if JARS:
                    jar_path = module / 'build/libs' / f'admintools-{target}-1.0.0.jar'
                    with zipfile.ZipFile(jar_path) as jar:
                        metadata = json.loads(jar.read('fabric.mod.json'))
                        self.assertEqual(metadata['mixins'], [config_name])
                        config = json.loads(jar.read(config_name))
                        self.assertIn(mixin_class, config['mixins'])
                        if target == '26.3':
                            self.assertNotIn('admintools.mixins.json', jar.namelist())
                        self.assertIn('com/echubbuck/admintools/mixin/' + mixin_class + '.class', jar.namelist())
                else:
                    metadata = json.loads((module / 'src/main/resources/fabric.mod.json').read_text())
                    self.assertEqual(metadata['mixins'], [config_name])
                    expected_game_tests = ['com.echubbuck.admintools.test.AdminToolsGameTests']
                    if target == '26.3':
                        expected_game_tests.append('com.echubbuck.admintools.test.AdminTools26_3GameTests')
                    self.assertEqual(metadata['entrypoints']['fabric-gametest'], expected_game_tests)
                    config_path = module / 'src/main/resources' / config_name
                    self.assertTrue(config_path.is_file(), f'missing target mixin config: {config_path}')
                    if not config_path.is_file():
                        continue
                    config = json.loads(config_path.read_text())
                    self.assertIn(mixin_class, config['mixins'])
                    source_path = ROOT / source_name
                    self.assertTrue(source_path.is_file(), f'missing target mixin source: {source_path}')
                    if source_path.is_file():
                        self.assertIn(descriptor, source_path.read_text())

if __name__ == '__main__':
    unittest.main()
