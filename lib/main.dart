import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

void main() {
  runApp(const ScreenShareApp());
}

const platform = MethodChannel('com.yourapp/screen_capture');

class ScreenShareApp extends StatelessWidget {
  const ScreenShareApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: ScreenShareScreen(),
    );
  }
}

class ScreenShareScreen extends StatefulWidget {
  @override
  _ScreenShareScreenState createState() => _ScreenShareScreenState();
}

class _ScreenShareScreenState extends State<ScreenShareScreen> {
  String? localIp;

  @override
  void initState() {
    super.initState();
    _startLocalServer();
  }

  Future<void> _startLocalServer() async {
    // Get the local IP address
    for (var interface in await NetworkInterface.list()) {
      for (var addr in interface.addresses) {
        if (addr.type == InternetAddressType.IPv4 && !addr.isLoopback) {
          setState(() {
            localIp = addr.address;
          });
          break;
        }
      }
    }

    // Start the HTTP server
    HttpServer server = await HttpServer.bind(localIp, 8080);

    print('Server running on http://$localIp:8080');

    server.listen((HttpRequest request) async {
      // Capture and encode the current screen frame
      Uint8List? jpegBytes = await _captureScreenAsJpeg();

      if (jpegBytes != null) {
        // Serve the JPEG frame
        request.response
          ..headers.contentType = ContentType('image', 'jpeg')
          ..add(jpegBytes)
          ..close();
      } else {
        request.response
          ..statusCode = HttpStatus.internalServerError
          ..write('Failed to capture screen in flutter')
          ..close();
      }
    });
  }

  Future<Uint8List?> _captureScreenAsJpeg() async {
    const platform = MethodChannel('com.yourapp/screen_capture');

    try {
      // Call the native Android method to capture the screen
      final Uint8List? jpegBytes = await platform.invokeMethod('captureScreen');

      return jpegBytes;
    } catch (e) {
      print('Error capturing screen: $e');
      return null;
    }
  }

// Request permission to capture the screen
  Future<void> startProjection() async {
    try {
      await platform.invokeMethod('startProjection');
    } catch (e) {
      print('Error starting projection: $e');
    }
  }

// Capture the screen
  Future<Uint8List?> captureScreen() async {
    try {
      final Uint8List? jpegBytes = await platform.invokeMethod('captureScreen');
      return jpegBytes;
    } catch (e) {
      print('Error capturing screen: $e');
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (localIp == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Screen Share')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
                onPressed: () {
                  startProjection();
                },
                child: Text("start")),
            ElevatedButton(
                onPressed: () {
                  captureScreen();
                },
                child: Text("Capture")),
            Text('Server running on http://$localIp:8080'),
            SizedBox(height: 10),
            const Text('Scan the QR Code to Connect:'),
            QrImageView(
              data: 'http://$localIp:8080/stream',
              version: QrVersions.auto,
              size: 200.0,
            ),
          ],
        ),
      ),
    );
  }
}
