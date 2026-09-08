import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';

final stoolAnalysisServiceProvider = Provider<StoolAnalysisService>((ref) {
  return StoolAnalysisService();
});

class StoolAnalysisService {
  Future<Map<String, dynamic>> requestAnalysis({
    required int petId,
    required String imageUrl,
    int? walkSpotId,
  }) async {
    final response = await ApiClient().dio.post(
      '/api/v1/health/stool-analyses',
      data: {
        'petId': petId,
        'imageUrl': imageUrl,
        'walkSpotId': ?walkSpotId,
      },
    );
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> getAnalysis(int id) async {
    final response =
        await ApiClient().dio.get('/api/v1/health/stool-analyses/$id');
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> getHistory(int petId, {int page = 0}) async {
    final response = await ApiClient().dio.get(
      '/api/v1/health/stool-analyses',
      queryParameters: {'petId': petId, 'page': page},
    );
    return response.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> getHealthTrend(int petId) async {
    final response = await ApiClient().dio.get(
      '/api/v1/health/stool-analyses/trend',
      queryParameters: {'petId': petId},
    );
    return response.data as Map<String, dynamic>;
  }
}
