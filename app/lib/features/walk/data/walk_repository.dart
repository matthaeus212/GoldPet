import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import 'walk_models.dart';

final walkRepositoryProvider = Provider<WalkRepository>((ref) {
  return WalkRepository(ref.read(apiClientProvider));
});

class WalkRepository {
  final ApiClient _apiClient;

  WalkRepository(this._apiClient);

  Future<WalkResponse> createWalk(CreateWalkRequest request) async {
    final response = await _apiClient.post(
      '/api/v1/walks',
      data: request.toJson(),
    );
    return WalkResponse.fromJson(response.data);
  }

  Future<List<WalkResponse>> getMyWalks() async {
    final response = await _apiClient.get('/api/v1/walks/my');
    return (response.data as List)
        .map((e) => WalkResponse.fromJson(e))
        .toList();
  }

  Future<WalkResponse> getWalk(int walkId) async {
    final response = await _apiClient.get('/api/v1/walks/$walkId');
    return WalkResponse.fromJson(response.data);
  }
}
