const test = require('node:test');
const assert = require('node:assert/strict');
const { isChartTool, isToolError, parseCharts, addCharts } =
    require('../../main/resources/static/js/chart-results.js');
const signed = 'https://example.test/chart.png?X-Amz-Signature=abc&expires=123';

test('recognizes AntV charts, diagrams, maps, spreadsheets and Spring-prefixed history', () => {
    for (const name of ['generate_line_chart', 'generate_pin_map', 'generate_flow_diagram',
        'generate_network_graph', 'generate_spreadsheet', 'generateChart',
        'dodo_chart_generate_bar_chart', 'old-client_generate_word_cloud_chart']) {
        assert.ok(isChartTool(name), name);
    }
    assert.equal(isChartTool('executeSql'), false);
    assert.equal(isChartTool('generate_unknown_payload'), false);
});
test('preserves signed URL and input title through full MCP and JSON text wrappers', () => {
    const result = { isError: false, content: [
        { type: 'text', text: JSON.stringify({ imageUrl: signed }) }
    ] };
    const charts = parseCharts(JSON.stringify(result), 'generate_bar_chart', '{"title":"销售比较"}');
    assert.equal(charts.length, 1);
    assert.equal(charts[0].url, signed);
    assert.equal(charts[0].title, '销售比较');
});
test('handles old Markdown, query strings and plain extensionless image URLs', () => {
    assert.equal(parseCharts('![旧图](' + signed + ')', 'generate_chart')[0].url, signed);
    assert.equal(parseCharts('![旧图](' + signed + ')', 'generate_chart')[0].title, '旧图');
    assert.equal(parseCharts('https://example.test/output/123', 'generate_line_chart')[0].url,
        'https://example.test/output/123');
});
test('reads structuredContent and multiple images, deduplicating repeated content', () => {
    const charts = parseCharts({
        structuredContent: { url: signed, title: '标题' },
        content: [{ type: 'text', text: signed }, { type: 'text', text: 'https://example.test/b.png' }]
    }, 'generate_pie_chart');
    assert.equal(charts.length, 2);
    const output = [];
    addCharts(output, charts);
    addCharts(output, charts);
    assert.equal(output.length, 2);
});
test('keeps map HTML and resource links as links, and static map images as images', () => {
    assert.equal(parseCharts({ url: 'https://example.test/map.html' }, 'generate_pin_map')[0].kind, 'link');
    assert.equal(parseCharts({ url: signed }, 'generate_pin_map')[0].kind, 'image');
    assert.equal(parseCharts({ type: 'resource_link', uri: 'https://example.test/data.xlsx' },
        'generate_spreadsheet')[0].kind, 'link');
});
test('does not render failed results or unlinked map data as successful image cards', () => {
    const failed = { isError: true, content: [{ type: 'text', text: signed }] };
    assert.ok(isToolError(JSON.stringify(failed)));
    assert.deepEqual(parseCharts(failed, 'generate_bar_chart'), []);
    assert.deepEqual(parseCharts({ result: failed }, 'generate_bar_chart'), []);
    assert.deepEqual(parseCharts({ type: 'pin-map', data: ['某地'] }, 'generate_pin_map'), []);
});
test('rejects dangerous protocols and unsafe embedded image types', () => {
    for (const url of ['javascript:alert(1)', 'file:///etc/passwd', 'data:text/html,hello',
        'https://user:pass@example.test/a.png']) {
        assert.deepEqual(parseCharts({ url }, 'generate_chart'), []);
    }
    assert.deepEqual(parseCharts({ type: 'image', mimeType: 'text/html', data: 'AAAA' },
        'generate_chart'), []);
    assert.equal(parseCharts({ type: 'image', mimeType: 'image/png', data: 'AAAA' },
        'generate_chart')[0].url, 'data:image/png;base64,AAAA');
});
