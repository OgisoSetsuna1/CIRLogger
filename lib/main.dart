import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';

void main() => runApp(AudioExperimentApp());

class AudioExperimentApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Audio Experiment',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: AudioControlPage(),
    );
  }
}

class AudioControlPage extends StatefulWidget {
  @override
  _AudioControlPageState createState() => _AudioControlPageState();
}

class _AudioControlPageState extends State<AudioControlPage> {
  final _frequencyController = TextEditingController(text: '15000');
  final _durationController = TextEditingController(text: '100');
  bool _useSpeaker = false;
  bool _isPlaying = false;
  String _status = 'Ready';
  String? _filePath;

  static const _audioChannel = MethodChannel('audio_experiment/control');
  static const _recordChannel = MethodChannel('audio_experiment/record');

  Future<void> _generateTone() async {
    setState(() {
      _isPlaying = true;
      _status = 'Playing...';
    });

    try {
      // 请求权限
      if (!await _requestPermissions()) return;

      // 开始录音
      final dir = await getApplicationDocumentsDirectory();
      final path =
          '${dir.path}/recording_${DateTime.now().millisecondsSinceEpoch}.wav';
      await _recordChannel.invokeMethod('startRecord', {'path': path});

      // 播放音频
      await _audioChannel.invokeMethod('playTone', {
        'frequency': int.parse(_frequencyController.text),
        'durationMs': int.parse(_durationController.text),
        'useSpeaker': _useSpeaker,
      });

      // 停止录音
      final result = await _recordChannel.invokeMethod('stopRecord');
      setState(() {
        _filePath = result;
        _status = 'Saved to: ${result ?? 'Failed'}';
      });
    } on PlatformException catch (e) {
      setState(() => _status = 'Error: ${e.message}');
    } finally {
      setState(() => _isPlaying = false);
    }
  }

  Future<bool> _requestPermissions() async {
    final status = await Permission.microphone.request();
    if (!status.isGranted) {
      setState(() => _status = '需要麦克风权限');
      return false;
    }
    return true;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Audio Experiment')),
      body: Padding(
        padding: EdgeInsets.all(20),
        child: Column(
          children: [
            TextField(
              controller: _frequencyController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(labelText: '频率 (Hz)'),
            ),
            TextField(
              controller: _durationController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(labelText: '持续时间 (ms)'),
            ),
            SwitchListTile(
              title: Text('使用扬声器'),
              value: _useSpeaker,
              onChanged: (v) => setState(() => _useSpeaker = v),
            ),
            ElevatedButton(
              onPressed: _isPlaying ? null : _generateTone,
              child: Text('播放并录音'),
            ),
            SizedBox(height: 20),
            Text(_status),
            if (_filePath != null)
              ElevatedButton(
                onPressed: () => _openFile(_filePath!),
                child: Text('打开录音文件'),
              ),
          ],
        ),
      ),
    );
  }

  void _openFile(String path) async {
    if (Platform.isAndroid) {
      await _recordChannel.invokeMethod('openFile', {'path': path});
    }
  }
}
